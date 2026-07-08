package com.roshdeandishe.student.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface MembershipDao {

    @Query("DELETE FROM memberships")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(memberships: List<MembershipEntity>)

    @Query("SELECT * FROM memberships")
    suspend fun getCachedMemberships(): List<MembershipEntity>

    // لیست کامل جایگزین می‌شود، نه merge - سرور همیشه منبع درستی کامل است
    // (بدون فیلتر جزئی/صفحه‌بندی در این endpoint)، پس آشتی‌دادن ردیف‌به‌ردیف
    // معنایی ندارد.
    @Transaction
    suspend fun replaceAll(memberships: List<MembershipEntity>) {
        clear()
        upsertAll(memberships)
    }
}
