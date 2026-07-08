// نسخه‌ی پلاگین‌ها فقط اینجا اعلام می‌شود (apply false) - هر ماژول واقعی
// (فعلاً فقط :app) در build.gradle.kts خودش apply می‌کند. تک‌ماژولی نگه
// داشته شده چون این پروژه در این اندازه به یک chunk چندماژولی نیاز ندارد.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
