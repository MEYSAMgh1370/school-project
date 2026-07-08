package com.roshdeandishe.student.di

import android.content.Context
import com.roshdeandishe.student.data.SessionState
import com.roshdeandishe.student.data.TokenStore
import com.roshdeandishe.student.data.remote.ApiService
import com.roshdeandishe.student.data.remote.AuthInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // آدرس پیش‌فرض dev: 10.0.2.2 روی امولاتور اندروید به localhost همان
    // ماشین میزبان اشاره دارد. قبل از هر استقرار واقعی، این باید به یک
    // BuildConfig field (per build variant) تبدیل شود - در v1 عمداً هاردکد
    // است تا فقط pipeline اثبات شود، نه پیکربندی build variant.
    private const val BASE_URL = "http://10.0.2.2:8080/"

    // بدون هیچ Factory دستی: adapterهای تولیدشده توسط moshi-kotlin-codegen
    // (برای هر @JsonClass) طبق قرارداد نام‌گذاری، خودکار توسط Moshi پیدا
    // می‌شوند.
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    // SessionState از @Inject constructor خودش ساخته می‌شود (پایین‌تر) -
    // اینجا @Provides جداگانه لازم نیست.

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore, sessionState: SessionState): AuthInterceptor =
        AuthInterceptor(tokenStore, sessionState)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
