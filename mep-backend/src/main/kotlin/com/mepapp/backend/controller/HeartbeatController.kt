package com.mepapp.backend.controller

import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class HeartbeatResponse(val status: String, val timestamp: Long)
data class AppStatusResponse(val isLive: Boolean, val lastHeartbeat: Long?, val staffId: String?)

@RestController
@RequestMapping("/api/heartbeat")
class HeartbeatController {

    companion object {
        // Store last heartbeat time per staff (in-memory for simplicity)
        private val heartbeats = ConcurrentHashMap<String, Long>()
    }

    @PostMapping
    fun sendHeartbeat(@RequestHeader("Authorization") auth: String): HeartbeatResponse {
        // Extract staff ID from token (simplified - in real app, decode JWT)
        val timestamp = Instant.now().toEpochMilli()
        // Use "app" as generic key for single app tracking
        heartbeats["app"] = timestamp
        return HeartbeatResponse("ok", timestamp)
    }

    @GetMapping("/status")
    fun getStatus(): AppStatusResponse {
        val lastHeartbeat = heartbeats["app"]
        val isLive = if (lastHeartbeat != null) {
            // Consider live if heartbeat within last 2 minutes
            (Instant.now().toEpochMilli() - lastHeartbeat) < 120000
        } else {
            false
        }
        return AppStatusResponse(isLive, lastHeartbeat, "app")
    }
}
