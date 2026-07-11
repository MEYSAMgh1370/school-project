package com.roshdeandishe.student.data

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «مدرسه‌ی فعلی» برای v1: فقط در حافظه، نه persist شده - هر بار اپ باز
 * می‌شود، از روی اولین Membership فعالِ کاربر دوباره محاسبه می‌شود
 * (DashboardViewModel این را بعد از خواندن /api/me/memberships ست می‌کند).
 * عمداً هیچ سوییچر مدرسه/نقشی مثل navbar وب ساخته نشده - آن یک تصمیم UX
 * جداگانه و غیر-بدیهی است (دو dropdown، معنای localStorage - رجوع به
 * CLAUDE.md §11 Q2) که ارزشش را برای یک shell فقط-خواندنی ندارد.
 * <p>
 * AuthInterceptor مقدار این کلاس را synchronous می‌خواند (نه suspend)،
 * پس AtomicReference به‌جای StateFlow/مقدار suspend انتخاب شده.
 */
@Singleton
class SessionState @Inject constructor() {

    private val currentSchoolId = AtomicReference<Long?>(null)

    fun currentSchoolIdOrNull(): Long? = currentSchoolId.get()

    fun setCurrentSchoolId(schoolId: Long?) {
        currentSchoolId.set(schoolId)
    }
}
