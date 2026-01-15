package com.mepapp.mobile.network

import retrofit2.http.*
import java.util.*

interface MepApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/call-logs")
    suspend fun logCall(@Body request: CallLogRequest): CallLogResponse

    @POST("api/call-logs/batch")
    suspend fun logCalls(@Body requests: List<CallLogRequest>): List<CallLogResponse>

    @POST("api/invoices/generate/{jobId}")
    suspend fun generateInvoice(
        @Path("jobId") jobId: String,
        @Query("materialCharge") material: Double,
        @Query("serviceCharge") service: Double
    ): InvoiceResponse
    @GET("api/jobs/staff/{staffId}")
    suspend fun getJobs(@Path("staffId") staffId: String): List<JobResponse>

    @GET("api/auth/me")
    suspend fun getMe(): UserResponse
}

data class UserResponse(
    val id: String,
    val name: String,
    val phone: String,
    val role: String
)

data class JobResponse(
    val id: String,
    val customerName: String,
    val serviceType: String,
    val status: String
)

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val token: String, val role: String, val id: String)

data class CallLogRequest(
    val jobId: String? = null,
    val staffId: String,
    val phoneNumber: String,
    val duration: Long,
    val callType: String,
    val timestamp: String? = null // ISO format
)
data class CallLogResponse(val id: String)

data class InvoiceResponse(val id: String, val invoiceNumber: String, val finalAmount: Double)
