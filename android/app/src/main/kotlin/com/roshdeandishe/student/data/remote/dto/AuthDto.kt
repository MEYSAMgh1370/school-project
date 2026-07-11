package com.roshdeandishe.student.data.remote.dto

import com.squareup.moshi.JsonClass

// همه‌ی این DTOها دقیقاً با شکل JSON بک‌اند مطابقت دارند - رجوع به
// iam.shared.AuthController (LoginRequest/TokenResponse), iam.MeController
// (MeResponse/MyMembershipResponse)، و academic.MyEnrollmentController
// (MyEnrollmentResponse) در پروژه‌ی اصلی.

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val userId: Long,
    val email: String,
    val fullName: String
)

@JsonClass(generateAdapter = true)
data class MembershipResponse(
    val membershipId: Long,
    val schoolId: Long,
    val roleCode: String,
    val active: Boolean
)

@JsonClass(generateAdapter = true)
data class EnrollmentResponse(
    val schoolId: Long,
    val classId: Long,
    val className: String,
    val section: String?
)
