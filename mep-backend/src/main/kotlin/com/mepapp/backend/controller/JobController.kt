package com.mepapp.backend.controller

import com.mepapp.backend.entity.Job
import com.mepapp.backend.entity.JobItem
import com.mepapp.backend.service.JobService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/jobs")
class JobController(private val jobService: JobService) {

    @GetMapping
    fun getAllJobs() = jobService.getAllJobs()

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: UUID) = jobService.getJob(id)

    @GetMapping("/staff/{staffId}")
    fun getJobsByStaff(@PathVariable staffId: UUID) = jobService.getJobsByStaff(staffId)

    @PostMapping("/{id}/start")
    fun startJob(@PathVariable id: UUID) = jobService.startJob(id)

    @PostMapping("/{id}/qr-verify")
    fun verifyQr(@PathVariable id: UUID) = jobService.verifyQr(id)

    @PostMapping("/{id}/complete")
    fun completeJob(@PathVariable id: UUID, @RequestBody items: List<JobItem>) = 
        jobService.completeJob(id, items)
}
