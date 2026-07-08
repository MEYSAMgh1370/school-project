package com.roshdeandishe.student.data.remote

import com.roshdeandishe.student.data.SessionState
import com.roshdeandishe.student.data.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * دو کار برای هر Request خروجی: (۱) هدر Authorization: Bearer <token> را
 * اضافه می‌کند - جز برای خودِ endpoint صدور توکن (چیکن-اند-اگ: هنوز توکنی
 * وجود ندارد وقتی داریم آن را درخواست می‌کنیم)، و (۲) هدر X-School-ID را
 * فقط برای endpointهایی که واقعاً به context مدرسه نیاز دارند اضافه
 * می‌کند (فعلاً فقط /api/me/enrollment - /api/me و /api/me/memberships
 * عمداً بدون آن هم در بک‌اند کار می‌کنند، رجوع به TenantResolverFilter).
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val sessionState: SessionState
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        if (path.endsWith("/api/auth/token")) {
            return chain.proceed(original)
        }

        val requestBuilder = original.newBuilder()

        tokenStore.currentTokenOrNull()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        if (path.endsWith("/api/me/enrollment")) {
            sessionState.currentSchoolIdOrNull()?.let { schoolId ->
                requestBuilder.addHeader("X-School-ID", schoolId.toString())
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
