package com.example.roshdeandishe.academic;

import java.util.List;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Academic.
 */
public interface AcademicFacade {

    /**
     * ثبت‌نام یک دانش‌آموز در یک کلاس مشخص. Idempotent است: اگر همین کاربر
     * از قبل در همین مدرسه دانش‌آموز ثبت‌شده باشد، به‌جای خطا یا رکورد
     * تکراری، فقط classId او به‌روزرسانی می‌شود (یعنی جابه‌جایی کلاس).
     * <p>
     * این متد به‌صورت داخلی IamFacade را صدا می‌زند تا (در صورت نبود)
     * کاربر و Membership نقش STUDENT را بسازد - دقیقاً همان الگویی که
     * SchoolController برای ساخت خودکار SCHOOL_ADMIN هنگام ساخت مدرسه
     * استفاده کرد.
     */
    StudentId enrollStudent(String email, String fullName, Long schoolId, Long classId);

    StudentInfo getStudentInfo(Long studentId);

    List<StudentInfo> listStudents(Long schoolId);

    ClassId createClass(Long schoolId, String name, Long teacherId, String section);

    List<ClassInfo> listClasses(Long schoolId);

    /**
     * لینک کردن یک کاربر به‌عنوان ولیِ یک دانش‌آموز. Idempotent است: اگر
     * همین (studentId, guardianUserId) از قبل لینک شده باشد، همان لینک
     * موجود بازگردانده می‌شود.
     * <p>
     * اعتبارسنجی: guardianUserId باید یک Membership فعال با نقش PARENT در
     * همان schoolId داشته باشد - وگرنه AcademicError.GuardianNotEligible.
     * این یک بررسی امنیتی است، نه صرفاً داده‌ای: بدون آن هر کاربری
     * (مثلاً یک TEACHER) می‌توانست به داده‌ی مالی/تحصیلیِ یک دانش‌آموز
     * لینک شود.
     */
    GuardianLinkId linkGuardian(Long schoolId, Long studentId, Long guardianUserId);

    /**
     * همه‌ی ولی‌های لینک‌شده به یک دانش‌آموز خاص.
     */
    List<Long> listGuardians(Long studentId);

    /**
     * همه‌ی دانش‌آموزانی که این کاربر در این مدرسه ولیِ آن‌هاست - نقطه‌ی
     * ورودی اصلی برای «سرویس‌دهی خودکار» یک PARENT (مثلاً از طریق ربات
     * بله): فقط دانش‌آموزان واقعاً لینک‌شده برمی‌گردد، نه کل دانش‌آموزان
     * مدرسه.
     */
    List<StudentInfo> listWards(Long guardianUserId, Long schoolId);

    record StudentId(Long value) {
    }

    record GuardianLinkId(Long value) {
    }

    record ClassId(Long value) {
    }

    record StudentInfo(StudentId id, Long userId, Long schoolId, ClassId classId) {
    }

    record ClassInfo(ClassId id, Long schoolId, String name, Long teacherId, String section) {
    }
}
