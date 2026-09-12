package com.example.nursingdevice

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class NurseRequest(
    @SerializedName("nurseId") val nurseId: String,
    @SerializedName("name") val name: String,
    @SerializedName("age") val age: Int?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("pointOfCare") val pointOfCare: String,
    @SerializedName("contactNo") val contactNo: String?,
    @SerializedName("pin") val pin: String? = null
)

data class NurseLoginRequest(
    @SerializedName("nurseId") val nurseId: String,
    @SerializedName("pin") val pin: String
)

data class NurseApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("nurse") val nurse: NurseData?,
    @SerializedName("credentials") val credentials: Credentials? = null,
    @SerializedName("credentialError") val credentialError: String? = null
)

/** Nurse profile plus credentials — returned by both register and login. */
data class NurseRegistration(
    val nurse: NurseData,
    val credentials: Credentials?
)

data class NurseData(
    @SerializedName("nurseId") val nurseId: String,
    @SerializedName("name") val name: String,
    @SerializedName("age") val age: Int?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("pointOfCare") val pointOfCare: String,
    @SerializedName("contactNo") val contactNo: String?
)

interface NurseApiService {
    @POST("api/nurses/register")
    suspend fun registerNurse(@Body request: NurseRequest): NurseApiResponse

    @POST("api/nurses/login")
    suspend fun loginNurse(@Body request: NurseLoginRequest): NurseApiResponse

    @GET("api/nurses/{nurseId}")
    suspend fun getNurse(@Path("nurseId") nurseId: String): NurseApiResponse
}

class NurseRepository {
    private val BASE_URL = "https://nfc-backend-ostp.onrender.com"
    private val api: NurseApiService

    init {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            // 60s to absorb a Render free-tier cold start (UptimeRobot keeps it warm,
            // but a missed ping can still leave the first request waking the instance).
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NurseApiService::class.java)
    }

    suspend fun register(
        nurseId: String, name: String, age: Int?,
        gender: String?, pointOfCare: String, contactNo: String?,
        pin: String? = null
    ): Result<NurseRegistration> = withContext(Dispatchers.IO) {
        try {
            val resp = api.registerNurse(NurseRequest(nurseId, name, age, gender, pointOfCare, contactNo, pin))
            if (resp.success && resp.nurse != null) {
                Result.success(NurseRegistration(resp.nurse, resp.credentials))
            } else {
                Result.failure(Exception(resp.message ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Log.e("NurseRepository", "register", e)
            val msg = if (e is retrofit2.HttpException) {
                parseErrorMessage(e) ?: "HTTP ${e.code()}"
            } else e.message ?: "Registration failed"
            Result.failure(Exception(msg))
        }
    }

    suspend fun login(nurseId: String, pin: String): Result<NurseRegistration> = withContext(Dispatchers.IO) {
        try {
            val resp = api.loginNurse(NurseLoginRequest(nurseId, pin))
            if (resp.success && resp.nurse != null) {
                Result.success(NurseRegistration(resp.nurse, resp.credentials))
            } else {
                Result.failure(Exception(resp.message ?: "Nurse login failed"))
            }
        } catch (e: Exception) {
            Log.e("NurseRepository", "login", e)
            val msg = if (e is retrofit2.HttpException) {
                parseErrorMessage(e) ?: if (e.code() == 401) "Invalid PIN. Please check your PIN and try again." else "HTTP ${e.code()}"
            } else {
                e.message ?: "Nurse login failed"
            }
            Result.failure(Exception(msg))
        }
    }

    private fun parseErrorMessage(e: retrofit2.HttpException): String? {
        return try {
            val body = e.response()?.errorBody()?.string().orEmpty()
            if (body.isBlank()) return null
            val root = com.google.gson.JsonParser().parse(body)
            if (root.isJsonObject && root.asJsonObject.has("message")) {
                root.asJsonObject.get("message").asString
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
