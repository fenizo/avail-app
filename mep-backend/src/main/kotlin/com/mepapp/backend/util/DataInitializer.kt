package com.mepapp.backend.util

import com.mepapp.backend.entity.Role
import com.mepapp.backend.entity.User
import com.mepapp.backend.entity.SystemConfig
import com.mepapp.backend.repository.SystemConfigRepository
import com.mepapp.backend.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DataInitializer(
    private val userRepository: UserRepository,
    private val configRepository: SystemConfigRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        // Create Admin if not exists
        if (userRepository.findByPhone("9999999999") == null) {
            val admin = User(
                name = "Admin User",
                phone = "9999999999",
                passwordHash = passwordEncoder.encode("10006"),
                role = Role.ADMIN
            )
            userRepository.save(admin)
            println("Seeded Admin user: 9999999999 / 10006")
        }

        // Create Staff if not exists
        if (userRepository.findByPhone("1234567890") == null) {
            val staff = User(
                name = "Field Staff",
                phone = "1234567890",
                passwordHash = passwordEncoder.encode("1234"),
                role = Role.STAFF
            )
            userRepository.save(staff)
            println("Seeded Staff user: 1234567890 / 1234")
        }

        // Create Default Configs
        if (configRepository.findByKey("sync_interval_minutes") == null) {
            configRepository.save(SystemConfig("sync_interval_minutes", "15"))
            println("Seeded default sync interval: 15 minutes")
        }
    }
}
