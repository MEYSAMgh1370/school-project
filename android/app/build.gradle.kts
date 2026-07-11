plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.roshdeandishe.student"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roshdeandishe.student"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // شبکه: صدور/اعتبارسنجی JWT مسئول بک‌اند است؛ اینجا فقط مصرف‌کننده‌ی
    // /api/auth/token و /api/me/** هستیم (رجوع به data/remote/ApiService)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // فقط codegen (نه moshi-kotlin reflection-based) - همه‌ی DTOها
    // @JsonClass(generateAdapter = true) دارند، پس نیازی به kotlin-reflect
    // (وابستگی سنگین‌تر نسخه‌ی reflection) نیست.
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // ذخیره‌سازی آفلاین - فقط پروفایل و memberships (v1 عمداً thin است)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // نگه‌داری امن توکن - رجوع به data/TokenStore. رمزنگاری مستقیماً با
    // java.security.KeyStore/javax.crypto (پلتفرم اندروید) انجام می‌شود،
    // نه androidx.security-crypto's MasterKey - آن API برای پشتیبانی از
    // EncryptedFile/EncryptedSharedPreferences طراحی شده، نه برای دادن یک
    // SecretKey خام برای مصرف دلخواه؛ دسترسی مستقیم به AndroidKeyStore
    // ساده‌تر و صریح‌تر است.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
