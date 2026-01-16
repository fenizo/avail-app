package com.mepapp.backend.controller

import com.mepapp.backend.entity.Role
import com.mepapp.backend.entity.User
import com.mepapp.backend.repository.UserRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('ADMIN')")
class StaffController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    @GetMapping
    fun getAllStaff(): List<UserResponse> {
        return userRepository.findByRole(Role.STAFF).map { 
            UserResponse(it.id!!, it.name, it.phone, it.role.name)
        }
    }

    @PostMapping
    fun createStaff(@RequestBody request: CreateStaffRequest): UserResponse {
        if (userRepository.findByPhone(request.phone) != null) {
            throw RuntimeException("Staff with this phone number already exists")
        }

        val staff = User(
            name = request.name,
            phone = request.phone,
            passwordHash = passwordEncoder.encode(request.password),
            role = Role.STAFF
        )
        val saved = userRepository.save(staff)
        return UserResponse(saved.id!!, saved.name, saved.phone, saved.role.name)
    }

    @DeleteMapping("/{id}")
    fun deleteStaff(@PathVariable id: UUID) {
        userRepository.deleteById(id)
    }
}

data class CreateStaffRequest(val name: String, val phone: String, val password: String)

data class UserResponse(val id: UUID, val name: String, val phone: String, val role: String)
