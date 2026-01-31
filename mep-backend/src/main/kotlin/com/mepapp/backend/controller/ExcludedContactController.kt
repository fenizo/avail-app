package com.mepapp.backend.controller

import com.mepapp.backend.entity.ExcludedContact
import com.mepapp.backend.repository.ExcludedContactRepository
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/excluded-contacts")
class ExcludedContactController(
    private val excludedContactRepository: ExcludedContactRepository
) {
    // Get all excluded contacts
    @GetMapping
    fun getAllExcluded(): List<ExcludedContact> {
        return excludedContactRepository.findAll()
    }

    // Get just the phone numbers (for quick lookup)
    @GetMapping("/phones")
    fun getAllExcludedPhones(): List<String> {
        return excludedContactRepository.findAll().map { it.phoneNumber }
    }

    // Add a single excluded contact
    @PostMapping
    fun addExcluded(@RequestBody request: ExcludedContactRequest): ExcludedContact {
        val normalized = normalizePhone(request.phoneNumber)

        // Check if already exists (normalized)
        val existing = excludedContactRepository.findAll().find {
            normalizePhone(it.phoneNumber) == normalized
        }
        if (existing != null) {
            return existing
        }

        val contact = ExcludedContact(
            phoneNumber = request.phoneNumber,
            contactName = request.contactName
        )
        return excludedContactRepository.save(contact)
    }

    // Add multiple excluded contacts at once
    @PostMapping("/batch")
    fun addExcludedBatch(@RequestBody phones: List<String>): Map<String, Any> {
        var added = 0
        var skipped = 0

        phones.forEach { phone ->
            val normalized = normalizePhone(phone)
            val exists = excludedContactRepository.findAll().any {
                normalizePhone(it.phoneNumber) == normalized
            }
            if (!exists) {
                excludedContactRepository.save(ExcludedContact(phoneNumber = phone))
                added++
            } else {
                skipped++
            }
        }

        return mapOf(
            "status" to "success",
            "added" to added,
            "skipped" to skipped,
            "total" to excludedContactRepository.count()
        )
    }

    // Remove an excluded contact
    @DeleteMapping("/{phoneNumber}")
    @Transactional
    fun removeExcluded(@PathVariable phoneNumber: String): ResponseEntity<Map<String, String>> {
        val normalized = normalizePhone(phoneNumber)

        // Find and delete by normalized phone
        val toDelete = excludedContactRepository.findAll().filter {
            normalizePhone(it.phoneNumber) == normalized
        }

        if (toDelete.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        toDelete.forEach { excludedContactRepository.delete(it) }

        return ResponseEntity.ok(mapOf("status" to "deleted", "phoneNumber" to phoneNumber))
    }

    // Check if a phone is excluded
    @GetMapping("/check/{phoneNumber}")
    fun isExcluded(@PathVariable phoneNumber: String): Map<String, Boolean> {
        val normalized = normalizePhone(phoneNumber)
        val excluded = excludedContactRepository.findAll().any {
            normalizePhone(it.phoneNumber) == normalized
        }
        return mapOf("excluded" to excluded)
    }

    // Normalize phone number - remove +91 or 91 prefix
    private fun normalizePhone(phone: String): String {
        var normalized = phone.replace("\\s+".toRegex(), "").replace("-", "")
        if (normalized.startsWith("+91")) {
            normalized = normalized.substring(3)
        } else if (normalized.startsWith("91") && normalized.length > 10) {
            normalized = normalized.substring(2)
        }
        return normalized
    }
}

data class ExcludedContactRequest(
    val phoneNumber: String,
    val contactName: String? = null
)
