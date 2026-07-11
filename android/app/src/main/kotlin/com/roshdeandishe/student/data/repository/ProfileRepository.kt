package com.roshdeandishe.student.data.repository

import com.roshdeandishe.student.data.SessionState
import com.roshdeandishe.student.data.local.MembershipDao
import com.roshdeandishe.student.data.local.MembershipEntity
import com.roshdeandishe.student.data.local.ProfileDao
import com.roshdeandishe.student.data.local.ProfileEntity
import com.roshdeandishe.student.data.remote.ApiService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class Profile(val userId: Long, val email: String, val fullName: String)

data class Membership(val membershipId: Long, val schoolId: Long, val roleCode: String, val active: Boolean)

data class Enrollment(val schoolId: Long, val classId: Long, val className: String, val section: String?)

/**
 * Network-first, cache-fallback - نه یک sync engine عمومی: چون v1
 * فقط-خواندنی است، هیچ داده‌ای برای آشتی‌دادن با تغییرات محلی وجود ندارد.
 * <p>
 * فقط شکست شبکه‌ای واقعی (IOException - قطع اتصال/timeout) به کش برمی‌گردد؛
 * یک HttpException (مثلاً ۴۰۱/۴۰۳ به‌خاطر توکن منقضی) عمداً catch نمی‌شود
 * و باید تا ViewModel بالا برود - وگرنه کاربری که دیگر لاگین معتبر ندارد،
 * داده‌ی کهنه‌ی کش را انگار هنوز واردشده می‌بیند.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val apiService: ApiService,
    private val profileDao: ProfileDao,
    private val membershipDao: MembershipDao,
    private val sessionState: SessionState
) {

    suspend fun loadProfile(): Profile {
        return try {
            val response = apiService.me()
            profileDao.replace(ProfileEntity(response.userId, response.email, response.fullName))
            Profile(response.userId, response.email, response.fullName)
        } catch (e: IOException) {
            profileDao.getCachedProfile()?.let { Profile(it.userId, it.email, it.fullName) } ?: throw e
        }
    }

    suspend fun loadMemberships(): List<Membership> {
        val memberships = try {
            val response = apiService.memberships()
            membershipDao.replaceAll(
                response.map { MembershipEntity(it.membershipId, it.schoolId, it.roleCode, it.active) }
            )
            response.map { Membership(it.membershipId, it.schoolId, it.roleCode, it.active) }
        } catch (e: IOException) {
            membershipDao.getCachedMemberships().map { Membership(it.membershipId, it.schoolId, it.roleCode, it.active) }
        }

        // انتخاب "مدرسه‌ی فعلی" برای v1: اولین Membership فعال - بدون
        // سوییچر، بدون persist (SessionState، رجوع به توضیح خودش)
        memberships.firstOrNull { it.active }?.let { sessionState.setCurrentSchoolId(it.schoolId) }

        return memberships
    }

    /** نال یعنی «دانش‌آموز این مدرسه نیست» (۲۰۴ از بک‌اند) - نه یک خطا. */
    suspend fun loadEnrollment(schoolId: Long): Enrollment? {
        val response = apiService.enrollment(schoolId)
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        return Enrollment(body.schoolId, body.classId, body.className, body.section)
    }
}
