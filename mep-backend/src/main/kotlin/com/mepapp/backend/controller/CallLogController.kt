package com.mepapp.backend.controller

import com.mepapp.backend.entity.CallLog
import com.mepapp.backend.repository.CallLogRepository
import com.mepapp.backend.repository.JobRepository
import com.mepapp.backend.repository.UserRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/api/call-logs")
class CallLogController(
    private val callLogRepository: CallLogRepository,
    private val jobRepository: JobRepository,
    private val userRepository: UserRepository
) {
    @PostMapping
    fun logCall(@RequestBody request: CallLogRequest): CallLog {
        val staffUUID = UUID.fromString(request.staffId)
        val timestamp = request.timestamp ?: LocalDateTime.now()

        // Check 1: Duplicate check using phoneCallId (if provided)
        if (!request.phoneCallId.isNullOrBlank()) {
            val existing = callLogRepository.findByPhoneCallIdAndStaffId(request.phoneCallId, staffUUID)
            if (existing != null) {
                return existing // Return existing - don't create duplicate
            }
        }

        // Check 2: Duplicate check using phoneNumber + timestamp + staffId
        // This catches duplicates even when phoneCallId is null or different
        if (callLogRepository.existsByPhoneNumberAndTimestampAndStaffId(request.phoneNumber, timestamp, staffUUID)) {
            // Find and return existing record
            val existingLogs = callLogRepository.findByStaffIdOrderByTimestampDesc(staffUUID)
            val existing = existingLogs.find {
                it.phoneNumber == request.phoneNumber && it.timestamp == timestamp
            }
            if (existing != null) {
                return existing
            }
        }

        val staff = userRepository.findById(staffUUID).orElseThrow { RuntimeException("Staff not found") }
        val job = request.jobId?.let { jobId ->
            val jobUUID = UUID.fromString(jobId)
            jobRepository.findById(jobUUID).orElse(null)
        }

        val callLog = CallLog(
            job = job,
            staff = staff,
            phoneNumber = request.phoneNumber,
            duration = request.duration,
            callType = request.callType,
            contactName = request.contactName,
            timestamp = timestamp,
            phoneCallId = request.phoneCallId
        )
        return callLogRepository.save(callLog)
    }

    @PostMapping("/batch")
    fun logCalls(@RequestBody requests: List<CallLogRequest>): List<CallLog> {
        return requests.map { logCall(it) }
    }

    @GetMapping
    fun getAllLogs(): List<CallLog> {
        val allLogs = callLogRepository.findAllByOrderByTimestampDesc()

        // Deduplicate: keep only unique entries based on phoneNumber + timestamp + staffId
        val seen = mutableSetOf<String>()
        return allLogs.filter { log ->
            val key = "${log.phoneNumber}_${log.timestamp}_${log.staff.id}"
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
    }

    @GetMapping("/job/{jobId}")
    fun getLogsByJob(@PathVariable jobId: UUID): List<CallLog> =
        callLogRepository.findByJobIdOrderByTimestampDesc(jobId)

    @GetMapping("/staff/{staffId}")
    fun getLogsByStaff(@PathVariable staffId: UUID): List<CallLog> =
        callLogRepository.findByStaffIdOrderByTimestampDesc(staffId)

    @GetMapping("/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok")

    @DeleteMapping("/cleanup-duplicates")
    fun cleanupDuplicates(): Map<String, Any> {
        val allLogs = callLogRepository.findAllByOrderByTimestampDesc()
        val countBefore = allLogs.size

        // Group by phoneNumber + timestamp + staffId to find duplicates
        // Keep the first (most recent) one, delete the rest
        val seen = mutableSetOf<String>()
        val toDelete = mutableListOf<CallLog>()

        for (log in allLogs) {
            val key = "${log.phoneNumber}_${log.timestamp}_${log.staff.id}"
            if (seen.contains(key)) {
                toDelete.add(log)
            } else {
                seen.add(key)
            }
        }

        // Delete duplicates
        if (toDelete.isNotEmpty()) {
            callLogRepository.deleteAll(toDelete)
        }

        return mapOf(
            "status" to "success",
            "totalBefore" to countBefore,
            "duplicatesRemoved" to toDelete.size,
            "uniqueRemaining" to (countBefore - toDelete.size),
            "message" to "Removed ${toDelete.size} duplicate entries, kept ${countBefore - toDelete.size} unique records."
        )
    }

    @GetMapping("/export/excel")
    fun exportToExcel(): ResponseEntity<ByteArray> {
        val logs = callLogRepository.findAllByOrderByTimestampDesc()

        // Group by phone number to get unique contacts with their details
        val contactsMap = logs.groupBy { it.phoneNumber }

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Contacts")

        // Create header row with bold style
        val headerStyle = workbook.createCellStyle()
        val headerFont = workbook.createFont()
        headerFont.bold = true
        headerStyle.setFont(headerFont)

        val headerRow = sheet.createRow(0)
        val headers = listOf(
            "Phone Number",
            "Contact Name",
            "Total Calls",
            "Incoming Calls",
            "Outgoing Calls",
            "Missed Calls",
            "Total Duration (sec)",
            "Last Call Date",
            "Last Call Type",
            "Staff Name"
        )
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Add data rows - one row per unique contact
        var rowNum = 1
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        contactsMap.forEach { (phoneNumber, callLogs) ->
            val row = sheet.createRow(rowNum++)

            // Get the most recent call for contact details
            val latestCall = callLogs.maxByOrNull { it.timestamp }

            // Calculate stats
            val incomingCount = callLogs.count { it.callType == "INCOMING" }
            val outgoingCount = callLogs.count { it.callType == "OUTGOING" }
            val missedCount = callLogs.count { it.callType == "MISSED" }
            val totalDuration = callLogs.sumOf { it.duration }

            row.createCell(0).setCellValue(phoneNumber)
            row.createCell(1).setCellValue(latestCall?.contactName ?: "")
            row.createCell(2).setCellValue(callLogs.size.toDouble())
            row.createCell(3).setCellValue(incomingCount.toDouble())
            row.createCell(4).setCellValue(outgoingCount.toDouble())
            row.createCell(5).setCellValue(missedCount.toDouble())
            row.createCell(6).setCellValue(totalDuration.toDouble())
            row.createCell(7).setCellValue(latestCall?.timestamp?.format(dateFormatter) ?: "")
            row.createCell(8).setCellValue(latestCall?.callType ?: "")
            row.createCell(9).setCellValue(latestCall?.staff?.name ?: "")
        }

        // Auto-size columns
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        // Write to byte array
        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()

        val headers2 = HttpHeaders()
        headers2.contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        headers2.setContentDispositionFormData("attachment", "contacts_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.xlsx")

        return ResponseEntity.ok()
            .headers(headers2)
            .body(outputStream.toByteArray())
    }

}

data class CallLogRequest(
    val jobId: String?,
    val staffId: String,  // Changed from UUID to String
    val phoneNumber: String,
    val duration: Long,
    val callType: String,
    val contactName: String? = null,
    val timestamp: LocalDateTime? = null,
    val phoneCallId: String? = null  // Unique ID from mobile device to prevent duplicates
)
