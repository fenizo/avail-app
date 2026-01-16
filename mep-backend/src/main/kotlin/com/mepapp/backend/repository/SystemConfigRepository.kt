package com.mepapp.backend.repository

import com.mepapp.backend.entity.SystemConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemConfigRepository : JpaRepository<SystemConfig, String> {
    fun findByKey(key: String): SystemConfig?
}
