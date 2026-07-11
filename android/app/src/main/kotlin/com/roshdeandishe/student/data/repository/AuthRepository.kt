package com.roshdeandishe.student.data.repository

import com.roshdeandishe.student.data.TokenStore
import com.roshdeandishe.student.data.remote.ApiService
import com.roshdeandishe.student.data.remote.dto.LoginRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenStore: TokenStore
) {

    suspend fun login(email: String, password: String) {
        val response = apiService.issueToken(LoginRequest(email, password))
        tokenStore.saveToken(response.accessToken)
    }

    suspend fun logout() {
        // فقط حذف محلی توکن - v1 هیچ لیست سیاه/ابطال سمت سرور ندارد
        // (رجوع به CLAUDE.md: توکن تا انقضای طبیعی‌اش معتبر می‌ماند، یک
        // محدودیت مستندشده‌ی v1، نه یک سهو).
        tokenStore.clearToken()
    }

    /** باید قبل از رندر اولین صفحه یک‌بار صدا زده شود تا کش توکن پر شود. */
    suspend fun restoreSession() {
        tokenStore.loadIntoCache()
    }

    fun isLoggedIn(): Boolean = tokenStore.currentTokenOrNull() != null
}
