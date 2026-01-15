package com.mepapp.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

enum class JobStatus {
    NEW, IN_PROGRESS, COMPLETED
}

@Entity
@Table(name = "jobs")
data class Job(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: Customer,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    val staff: User,

    @Column(nullable = false)
    val serviceType: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: JobStatus = JobStatus.NEW,

    val startedAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,

    @Column(nullable = false)
    val qrDiscountApplied: Boolean = false,

    @OneToMany(mappedBy = "job", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<JobItem> = mutableListOf(),

    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
