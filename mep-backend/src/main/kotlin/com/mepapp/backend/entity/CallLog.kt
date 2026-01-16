package com.mepapp.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "call_logs")
data class CallLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id", nullable = true)
    val job: Job? = null,

    @ManyToOne(fetch = FetchType.EAGER) // Changed to EAGER to ensure serialization works
    @JoinColumn(name = "staff_id", nullable = false)
    val staff: User,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column(nullable = false)
    val duration: Long, // in seconds

    @Column(nullable = false)
    val callType: String, // INCOMING, OUTGOING, MISSED

    @Column(nullable = true)
    val contactName: String? = null,

    @Column(nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now()
)
