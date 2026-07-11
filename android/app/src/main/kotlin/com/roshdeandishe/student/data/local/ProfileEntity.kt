package com.roshdeandishe.student.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// یک ردیف فقط (v1 عمداً thin است، بدون چند-کاربره‌ی آفلاین): آخرین
// پروفایل موفق /api/me برای نمایش سریع بعد از باز شدن مجدد اپ، قبل از
// این‌که پاسخ شبکه برسد.
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val userId: Long,
    val email: String,
    val fullName: String
)
