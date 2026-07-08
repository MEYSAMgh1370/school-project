package com.roshdeandishe.student.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProfileDao {

    // فقط یک ردیف نگه‌داشته می‌شود: پروفایل تازه‌ی موفق، پروفایل قبلی را
    // جایگزین می‌کند - هیچ چندکاربره/تاریخچه‌ای لازم نیست.
    @Query("DELETE FROM profile")
    suspend fun clear()

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile LIMIT 1")
    suspend fun getCachedProfile(): ProfileEntity?

    suspend fun replace(profile: ProfileEntity) {
        clear()
        upsert(profile)
    }
}
