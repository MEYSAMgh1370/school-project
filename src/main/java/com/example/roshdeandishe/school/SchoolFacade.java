package com.example.roshdeandishe.school;

import java.util.List;
import java.util.Optional;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول School.
 * <p>
 * این Facade مسئول ثبت و مدیریت خود مدرسه‌هاست - نه عضویت‌ها یا
 * Permissionها (آن‌ها همچنان متعلق به IamFacade هستند). تفکیک عمدی:
 * "این مدرسه چیست؟" در اینجا، "چه کسی در این مدرسه چه نقشی دارد؟" در iam.
 */
public interface SchoolFacade {

    /**
     * ثبت یک مدرسه‌ی جدید در پلتفرم. این اولین قدم برای هر فرآیند
     * onboarding مدرسه‌ی جدید است؛ تا این متد صدا زده نشود، schoolId ای
     * که در IamFacade استفاده می‌شود معنای واقعی ندارد.
     */
    SchoolId createSchool(String name, String timezone, String locale);

    Optional<SchoolInfo> findSchool(SchoolId schoolId);

    /**
     * برای پنل مدیریت پلتفرم (PLATFORM_ADMIN) - لیست همه‌ی مدرسه‌های ثبت‌شده.
     */
    List<SchoolInfo> listSchools();

    /**
     * تغییر وضعیت یک مدرسه (مثلاً تعلیق یک مدرسه به دلیل عدم پرداخت).
     * توجه: این عملیات کاملاً مجزا از iam.setMembershipActive است - آن یکی
     * دسترسی *یک نفر* را در یک مدرسه قطع می‌کند، این یکی کل مدرسه را.
     */
    void setSchoolStatus(SchoolId schoolId, SchoolStatus status);

    record SchoolId(Long value) {
    }

    record SchoolInfo(SchoolId id, String name, SchoolStatus status, String timezone, String locale) {
    }

    enum SchoolStatus {
        ACTIVE, SUSPENDED
    }
}
