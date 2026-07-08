package com.roshdeandishe.student.data.remote

import com.roshdeandishe.student.data.remote.dto.EnrollmentResponse
import com.roshdeandishe.student.data.remote.dto.LoginRequest
import com.roshdeandishe.student.data.remote.dto.MeResponse
import com.roshdeandishe.student.data.remote.dto.MembershipResponse
import com.roshdeandishe.student.data.remote.dto.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/token")
    suspend fun issueToken(@Body request: LoginRequest): TokenResponse

    @GET("api/me")
    suspend fun me(): MeResponse

    @GET("api/me/memberships")
    suspend fun memberships(): List<MembershipResponse>

    // Response<> عمداً به‌جای EnrollmentResponse خام برگردانده می‌شود: بدنه
    // ممکن است ۲۰۴ باشد (کاربر در این مدرسه دانش‌آموز نیست - یک حالت عادی،
    // نه خطا)، که باید صریح از یک بدنه‌ی واقعی جدا شود.
    @GET("api/me/enrollment")
    suspend fun enrollment(@Query("schoolId") schoolId: Long): Response<EnrollmentResponse>
}
