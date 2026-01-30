package com.mepapp.backend.controller

import com.mepapp.backend.entity.CallLog
import com.mepapp.backend.repository.CallLogRepository
import com.mepapp.backend.repository.JobRepository
import com.mepapp.backend.repository.UserRepository
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
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

        // Check for duplicate using phoneCallId (if provided)
        if (!request.phoneCallId.isNullOrBlank()) {
            if (callLogRepository.existsByPhoneCallIdAndStaffId(request.phoneCallId, staffUUID)) {
                // Return existing - don't create duplicate
                val existing = callLogRepository.findByStaffIdOrderByTimestampDesc(staffUUID)
                    .find { it.phoneCallId == request.phoneCallId }
                if (existing != null) {
                    return existing
                }
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
            timestamp = request.timestamp ?: LocalDateTime.now(),
            phoneCallId = request.phoneCallId
        )
        return callLogRepository.save(callLog)
    }

    @PostMapping("/batch")
    fun logCalls(@RequestBody requests: List<CallLogRequest>): List<CallLog> {
        return requests.map { logCall(it) }
    }

    @GetMapping
    fun getAllLogs(): List<CallLog> = callLogRepository.findAllByOrderByTimestampDesc()

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
        val allLogs = callLogRepository.findAll()

        // Group by (phoneNumber, timestamp, staffId) to find duplicates
        val grouped = allLogs.groupBy { Triple(it.phoneNumber, it.timestamp, it.staff.id) }

        var deletedCount = 0
        val idsToDelete = mutableListOf<UUID>()

        for ((_, logs) in grouped) {
            if (logs.size > 1) {
                // Keep the first one (oldest by ID), delete the rest
                val sortedLogs = logs.sortedBy { it.id.toString() }
                val toDelete = sortedLogs.drop(1) // Keep first, delete rest
                toDelete.forEach { log ->
                    log.id?.let { idsToDelete.add(it) }
                }
                deletedCount += toDelete.size
            }
        }

        // Delete duplicates
        if (idsToDelete.isNotEmpty()) {
            callLogRepository.deleteAllById(idsToDelete)
        }

        return mapOf(
            "status" to "success",
            "totalLogs" to allLogs.size,
            "duplicatesDeleted" to deletedCount,
            "remainingLogs" to (allLogs.size - deletedCount)
        )
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
