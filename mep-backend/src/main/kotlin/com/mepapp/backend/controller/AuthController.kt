package com.mepapp.backend.controller

import com.mepapp.backend.entity.Role
import com.mepapp.backend.entity.User
import com.mepapp.backend.repository.UserRepository
import com.mepapp.backend.security.JwtUtils
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.*

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val token: String, val role: Role, val name: String, val id: UUID)
data class RegisterRequest(val name: String, val phone: String, val password: String, val role: Role)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.phone, request.password)
        )
        val user = userRepository.findByPhone(request.phone)!!
        val token = jwtUtils.generateToken(user.phone)
        return LoginResponse(token, user.role, user.name, user.id)
    }

    @GetMapping("/me")
    fun getMe(): User? {
        val authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
        val phone = authentication.name
        return userRepository.findByPhone(phone)
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): String {
        val user = User(
            name = request.name,
            phone = request.phone,
            passwordHash = passwordEncoder.encode(request.password),
            role = request.role
        )
        userRepository.save(user)
        return "User registered successfully"
    }
}
