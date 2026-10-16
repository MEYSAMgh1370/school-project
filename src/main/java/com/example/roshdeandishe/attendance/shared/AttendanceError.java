package com.example.roshdeandishe.attendance.shared;

import java.time.LocalDate;

/**
 * مدل خطای دامنه‌محور مختص ماژول attendance (openspec/changes/attendance.md، «قوانین»).
 */
public sealed interface AttendanceError {

    /** روز، روزِ درسیِ مدرسه نیست (هفته‌ی مدرسه، تعطیلیِ تقویم) یا در آینده است. */
    record InvalidAttendanceDay(LocalDate day, String reason) implements AttendanceError {
    }

    /** کلاس در این مدرسه نیست (کلاسِ مدرسه‌ی دیگر هم همین است، وجودش لو نمی‌رود). */
    record ClassNotFound(Long classId) implements AttendanceError {
    }

    /** دانش‌آموز در این مدرسه نیست (دانش‌آموزِ مدرسه‌ی دیگر هم همین است). */
    record StudentNotFound(Long studentId) implements AttendanceError {
    }

    record SchoolNotFound(Long schoolId) implements AttendanceError {
    }

    /** کلاس به سالِ جاریِ مدرسه (یا «بدون سال») تعلق ندارد؛ حضور و غیاب فقط برای سالِ جاری ثبت می‌شود. */
    record ClassNotInCurrentYear(Long classId, String classYear, String currentYear) implements AttendanceError {
    }

    /** دانش‌آموزِ یک مُهر همان روز عضوِ این کلاس نیست. */
    record StudentNotInClass(Long studentId, Long classId) implements AttendanceError {
    }

    /** مُهرِ نامعتبر: وضعیتِ ناشناخته، دانش‌آموزِ تکراری یا خالی، یادداشتِ بیش از ۲۵۵ نویسه، بدنه‌ی بدون فهرست. */
    record InvalidMark(String reason) implements AttendanceError {
    }

    /** بازه‌ی گزارش ناقص، وارونه یا بلندتر از سقف است. */
    record InvalidRange(String reason) implements AttendanceError {
    }

    /** معلم فقط برای کلاسی ثبت می‌کند که خودش در آن تدریس می‌کند (بدون ATTENDANCE_RECORD_ANY). */
    record NotYourClass(Long classId) implements AttendanceError {
    }

    /** بدون ATTENDANCE_EDIT_PAST فقط روزِ جاری قابل ثبت/ویرایش است. */
    record PastEditNotAllowed(LocalDate day) implements AttendanceError {
    }

    /** عذر فقط توسط ولیِ فعالِ همان دانش‌آموز ثبت می‌شود. */
    record NotYourChild(Long studentId) implements AttendanceError {
    }

    /** عذرِ نامعتبر: یادداشتِ خالی یا بیش از ۵۰۰ نویسه، دانش‌آموزِ خالی، تصمیمِ نامعتبر. */
    record InvalidExcuse(String reason) implements AttendanceError {
    }

    /** دانش‌آموز آن روز مُهرِ ABSENT ندارد، پس چیزی برای عذر نیست. */
    record NoAbsenceToExcuse(Long studentId, LocalDate day) implements AttendanceError {
    }

    /** عذر قبلاً تأیید یا رد شده است. */
    record ExcuseAlreadyDecided(Long excuseId) implements AttendanceError {
    }

    record ExcuseNotFound(Long excuseId) implements AttendanceError {
    }
}
