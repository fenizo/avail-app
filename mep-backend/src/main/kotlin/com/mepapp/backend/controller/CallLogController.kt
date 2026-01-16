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
        val staff = userRepository.findById(request.staffId).orElseThrow { RuntimeException("Staff not found") }
        val job = request.jobId?.let { jobRepository.findById(it).orElse(null) }
        
        val callLog = CallLog(
            job = job,
            staff = staff,
            phoneNumber = request.phoneNumber,
            duration = request.duration,
            callType = request.callType,
            contactName = request.contactName,
            timestamp = request.timestamp ?: LocalDateTime.now()
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

    @GetMapping("/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok")

}

data class CallLogRequest(
    val jobId: UUID?,
    val staffId: UUID,
    val phoneNumber: String,
    val duration: Long,
    val callType: String,
    val contactName: String? = null,
    val timestamp: LocalDateTime? = null
)
