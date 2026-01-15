package com.mepapp.backend.service

import com.mepapp.backend.entity.Job
import com.mepapp.backend.entity.JobItem
import com.mepapp.backend.entity.JobStatus
import com.mepapp.backend.repository.JobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class JobService(private val jobRepository: JobRepository) {

    fun getJob(id: UUID): Job = jobRepository.findById(id).orElseThrow { Exception("Job not found") }

    fun getJobsByStaff(staffId: UUID): List<Job> = jobRepository.findByStaffId(staffId)

    @Transactional
    fun startJob(id: UUID): Job {
        val job = getJob(id)
        if (job.status != JobStatus.NEW) throw Exception("Cannot start job in ${job.status} status")
        return jobRepository.save(job.copy(status = JobStatus.IN_PROGRESS, startedAt = LocalDateTime.now()))
    }

    @Transactional
    fun verifyQr(id: UUID): Job {
        val job = getJob(id)
        if (job.status != JobStatus.IN_PROGRESS) throw Exception("Job must be IN_PROGRESS to verify QR")
        return jobRepository.save(job.copy(qrDiscountApplied = true))
    }

    @Transactional
    fun completeJob(id: UUID, items: List<JobItem>): Job {
        val job = getJob(id)
        if (job.status != JobStatus.IN_PROGRESS) throw Exception("Job must be IN_PROGRESS to complete")
        
        // Add items to the job
        job.items.clear()
        items.forEach { it.copy(job = job).let { item -> job.items.add(item) } }
        
        return jobRepository.save(job.copy(
            status = JobStatus.COMPLETED,
            completedAt = LocalDateTime.now()
        ))
    }

    fun getAllJobs(): List<Job> = jobRepository.findAllByOrderByCreatedAtDesc()
}
