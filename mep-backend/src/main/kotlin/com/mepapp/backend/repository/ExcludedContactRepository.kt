package com.mepapp.backend.repository

import com.mepapp.backend.entity.ExcludedContact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExcludedContactRepository : JpaRepository<ExcludedContact, UUID> {
    fun findByPhoneNumber(phoneNumber: String): ExcludedContact?
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun deleteByPhoneNumber(phoneNumber: String)
}
