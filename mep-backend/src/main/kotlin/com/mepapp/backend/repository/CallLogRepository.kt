package com.mepapp.backend.repository

import com.mepapp.backend.entity.CallLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface CallLogRepository : JpaRepository<CallLog, UUID> {
    fun findByJobIdOrderByTimestampDesc(jobId: UUID): List<CallLog>
    fun findByStaffIdOrderByTimestampDesc(staffId: UUID): List<CallLog>
    fun findAllByOrderByTimestampDesc(): List<CallLog>
    fun existsByPhoneCallIdAndStaffId(phoneCallId: String, staffId: UUID): Boolean

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM call_logs c1
        WHERE c1.id NOT IN (
            SELECT MIN(c2.id) FROM call_logs c2
            GROUP BY c2.phone_number, c2.timestamp, c2.staff_id
        )
    """, nativeQuery = true)
    fun deleteDuplicates(): Int
}
