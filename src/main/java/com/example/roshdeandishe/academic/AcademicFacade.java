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

    ClassId createClass(Long schoolId, String name, Long teacherId, String section);

    List<ClassInfo> listClasses(Long schoolId);

    record StudentId(Long value) {
    }

    record ClassId(Long value) {
    }

    record StudentInfo(StudentId id, Long userId, Long schoolId, ClassId classId) {
    }

    record ClassInfo(ClassId id, Long schoolId, String name, Long teacherId, String section) {
    }
}
