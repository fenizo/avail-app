package com.mepapp.backend.controller

import com.mepapp.backend.repository.UserRepository
import com.mepapp.backend.security.JwtUtils
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class HeartbeatResponse(val status: String, val timestamp: Long)
data class UserStatusResponse(
    val staffId: String,
    val staffName: String,
    val isLive: Boolean,
    val lastHeartbeat: Long?
)
data class AllStatusResponse(val users: List<UserStatusResponse>)

@RestController
@RequestMapping("/api/heartbeat")
class HeartbeatController(
    private val jwtUtils: JwtUtils,
    private val userRepository: UserRepository
) {

    companion object {
        // Store last heartbeat time per staff ID (in-memory for simplicity)
        // Key: staffId, Value: Pair(lastHeartbeat, staffName)
        private val heartbeats = ConcurrentHashMap<String, Pair<Long, String>>()
    }

    @PostMapping
    fun sendHeartbeat(@RequestHeader("Authorization") auth: String): HeartbeatResponse {
        val timestamp = Instant.now().toEpochMilli()

        // Extract user from JWT token
        try {
            val token = auth.removePrefix("Bearer ").trim()
            val phone = jwtUtils.getUsernameFromToken(token)
            val user = userRepository.findByPhone(phone)

            if (user != null) {
                heartbeats[user.id.toString()] = Pair(timestamp, user.name)
            }
        } catch (e: Exception) {
            // If token parsing fails, still accept heartbeat
        }

        return HeartbeatResponse("ok", timestamp)
    }

    @GetMapping("/status")
    fun getStatus(): AllStatusResponse {
        val currentTime = Instant.now().toEpochMilli()
        val twoMinutesAgo = currentTime - 120000

        val userStatuses = heartbeats.map { (staffId, data) ->
            val (lastHeartbeat, staffName) = data
            UserStatusResponse(
                staffId = staffId,
                staffName = staffName,
                isLive = lastHeartbeat > twoMinutesAgo,
                lastHeartbeat = lastHeartbeat
            )
        }.sortedByDescending { it.lastHeartbeat }

        return AllStatusResponse(userStatuses)
    }
}
