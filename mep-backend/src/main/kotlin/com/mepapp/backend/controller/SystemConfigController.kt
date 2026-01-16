package com.mepapp.backend.controller

import com.mepapp.backend.entity.SystemConfig
import com.mepapp.backend.repository.SystemConfigRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/settings")
class SystemConfigController(private val repository: SystemConfigRepository) {

    @GetMapping("/sync-interval")
    fun getSyncInterval(): Map<String, String> {
        val config = repository.findByKey("sync_interval_minutes")
        return mapOf("value" to (config?.value ?: "15"))
    }

    @PatchMapping("/sync-interval")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateSyncInterval(@RequestBody request: Map<String, String>): Map<String, String> {
        val newValue = request["value"] ?: throw RuntimeException("Value is required")
        
        // Validate it's a number
        newValue.toIntOrNull() ?: throw RuntimeException("Value must be a number")

        val config = repository.findByKey("sync_interval_minutes") ?: SystemConfig("sync_interval_minutes", "15")
        config.value = newValue
        repository.save(config)
        
        return mapOf("value" to config.value)
    }
}
