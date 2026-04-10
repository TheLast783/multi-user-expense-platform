package com.expensetracker.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class ExpenseSyncPayload(
    val title: String,
    val date: String,
    val amount: String,
    val isExpense: Boolean,
    val originalSms: String,
    val paymentId: String?,
    val assignedContact: String,
    val note: String,
    val type: String,
    val bank: String,
    val status: String,
    val remainingAmount: Double
)

data class AuthPayload(val authCode: String)
data class InvoicePayload(val contactName: String, val email: String)
data class ApiResponse(val status: String, val message: String)

data class RuleSyncPayload(
    val name: String,
    val email: String,
    val frequencyType: String,
    val frequencyValue: String,
    val currentCount: Int,
    val isEnabled: Boolean
)

interface ExpenseApi {
    @POST("/api/sync")
    suspend fun syncExpenses(
        @retrofit2.http.Header("X-User-Email") email: String, 
        @Body payloads: List<ExpenseSyncPayload>
    ): Any
    
    @POST("/api/auth/google")
    suspend fun authenticateGoogle(@Body payload: AuthPayload): Any
    
    @POST("/api/invoice/send")
    suspend fun sendInvoice(
        @retrofit2.http.Header("X-User-Email") userEmail: String,
        @Body payload: InvoicePayload
    ): ApiResponse

    @POST("/api/rules/sync")
    suspend fun syncRules(
        @retrofit2.http.Header("X-User-Email") email: String,
        @Body payloads: List<RuleSyncPayload>
    ): Any

    @retrofit2.http.DELETE("/api/rules/{name}")
    suspend fun deleteRule(
        @retrofit2.http.Header("X-User-Email") email: String,
        @retrofit2.http.Path("name") name: String
    ): Any

    @retrofit2.http.GET("/api/expenses/all")
    suspend fun fetchExpenses(
        @retrofit2.http.Header("X-User-Email") email: String
    ): List<ExpenseSyncPayload>

    @retrofit2.http.GET("/api/rules/all")
    suspend fun fetchRules(
        @retrofit2.http.Header("X-User-Email") email: String
    ): List<RuleSyncPayload>

    @POST("/api/admin/clear_database")
    suspend fun adminClearDatabase(
        @retrofit2.http.Header("X-User-Email") email: String
    ): ApiResponse
}

object RetrofitClient {
    // Use 10.0.2.2 for Emulator, or your PC's WiFi IP for a real device
    // Professional Cloud URL (Render)
    private val BASE_URL = com.expensetracker.BuildConfig.BASE_URL

    val api: ExpenseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExpenseApi::class.java)
    }
}
