package com.roshdeandishe.student.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memberships")
data class MembershipEntity(
    @PrimaryKey val membershipId: Long,
    val schoolId: Long,
    val roleCode: String,
    val active: Boolean
)
