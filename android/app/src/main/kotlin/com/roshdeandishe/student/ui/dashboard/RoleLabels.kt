package com.roshdeandishe.student.ui.dashboard

// همان نگاشت CLAUDE.md برای web.RoleLabels/people.js - کد خام نقش هرگز
// نباید مستقیم به کاربر نشان داده شود.
object RoleLabels {
    fun forCode(roleCode: String): String = when (roleCode) {
        "STUDENT" -> "دانش‌آموز"
        "TEACHER" -> "معلم"
        "PARENT" -> "والد"
        "SCHOOL_ADMIN" -> "مدیر مدرسه"
        "FINANCE_ADMIN" -> "مدیر مالی"
        "PLATFORM_ADMIN" -> "مدیر پلتفرم"
        else -> roleCode
    }
}
