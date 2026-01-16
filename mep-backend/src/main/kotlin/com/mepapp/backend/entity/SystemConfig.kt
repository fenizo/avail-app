package com.mepapp.backend.entity

import jakarta.persistence.*

@Entity
@Table(name = "system_config")
data class SystemConfig(
    @Id
    @Column(name = "config_key", nullable = false)
    val key: String,

    @Column(name = "config_value", nullable = false)
    var value: String
)
