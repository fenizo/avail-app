package com.mepapp.backend.repository

import com.mepapp.backend.entity.Job
import com.mepapp.backend.entity.JobStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface JobRepository : JpaRepository<Job, UUID> {
    fun findByStaffIdAndStatus(staffId: UUID, status: JobStatus): List<Job>
    fun findByStaffId(staffId: UUID): List<Job>
    fun findAllByOrderByCreatedAtDesc(): List<Job>
}
