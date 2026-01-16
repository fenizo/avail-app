package com.mepapp.backend.repository

import com.mepapp.backend.entity.CallLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CallLogRepository : JpaRepository<CallLog, UUID> {
    fun findByJobIdOrderByTimestampDesc(jobId: UUID): List<CallLog>
    fun findByStaffIdOrderByTimestampDesc(staffId: UUID): List<CallLog>
    fun findAllByOrderByTimestampDesc(): List<CallLog>
}
