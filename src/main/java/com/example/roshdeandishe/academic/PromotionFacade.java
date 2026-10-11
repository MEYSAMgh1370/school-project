package com.example.roshdeandishe.academic;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * سالِ تحصیلیِ جدید و ارتقای دانش‌آموزان (openspec/changes/academic-year.md، فاز ۳): انتسابِ داده‌ی موجود به یک
 * سال، اجرای ارتقا (خودکار یا دستی)، برگرداندنِ آن، و جا‌دادنِ دانش‌آموزانِ ارتقایافته در کلاس‌های سالِ جدید.
 * همه‌ی اجراها روی «سالِ جاریِ مدرسه» هستند؛ مدرسه‌ای که سالِ جاری ندارد YearNotChosen می‌گیرد.
 */
public interface PromotionFacade {

    /** کلاس‌ها و دانش‌آموزانی که هنوز به سالی نسبت داده نشده‌اند؛ {@code currentYear} تهی اگر سالِ جاری انتخاب نشده. */
    UnattributedSummary unattributedSummary(Long schoolId);

    /**
     * همه‌ی کلاس‌های بدون سال را به {@code academicYear} و برای هر دانش‌آموزِ فعالِ بدون ردیف یک ثبت‌نامِ ENROLLED
     * می‌نویسد (یک تراکنش). سال باید در فهرست و نه بعد از سالِ جاریِ مدرسه باشد؛ مدرسه‌ی بدون سالِ جاری
     * YearNotChosen. چیزی برای انتساب نبود: تغییری نمی‌کند.
     */
    AttributionResult attributeExistingData(Long schoolId, String academicYear);

    /** نتیجه‌ی پیش‌فرضِ هر دانش‌آموزِ ارتقاپذیر از {@code fromYear}، بدون نوشتن. */
    List<PlanItem> preview(Long schoolId, String fromYear);

    /**
     * ارتقای دانش‌آموزانِ {@code fromYear} به سالِ جاری. {@code decisions} نتیجه‌ی صریحِ بعضی دانش‌آموزان است؛
     * بقیه قاعده‌ی پیش‌فرض (پایه‌ی بعد، یا فارغ‌التحصیل در آخرین پایه‌ی مدرسه) را می‌گیرند.
     * InvalidRollover اگر سالِ مبدأ بعد از جاری باشد، دانش‌آموزی ارتقاپذیر نباشد، یا نتیجه با پایه نخواند.
     */
    RunInfo run(Long schoolId, String fromYear, Map<Long, PromotionOutcome> decisions);

    /**
     * ارتقای خودکار پس از تعویضِ سال (قاعده‌ی پیش‌فرض برای همه). خالی اگر تنظیمِ مدرسه خاموش است، سالِ جدید
     * بعد از سالِ مبدأ نیست یا دانش‌آموزی برای ارتقا نمانده.
     */
    Optional<RunInfo> runAutomatic(Long schoolId, String fromYear);

    /** اجراهای مدرسه، جدید به قدیم. */
    List<RunInfo> listRuns(Long schoolId);

    /**
     * برگرداندنِ یک اجرا: وضعیت و کلاسِ قبلیِ دانش‌آموزان برمی‌گردد، ثبت‌نام‌های ساخته‌شده پاک و عضویت‌های
     * غیرفعال‌شده فعال می‌شود. PromotionRunNotUndoable اگر قبلاً برگشته، اجرای جدیدتری هست، یا دانش‌آموزی از آن جا
     * داده شده.
     */
    void undo(Long schoolId, Long runId);

    /**
     * تغییرِ دستیِ نتیجه‌ی یک دانش‌آموزِ اجرای امسال که هنوز جا داده نشده (PENDING) یا فارغ‌التحصیل شده:
     * {@code REPEAT} (همان پایه) یا {@code PROMOTE} (پایه‌ی بعد).
     */
    void changeOutcome(Long schoolId, Long studentId, PromotionOutcome outcome);

    /** دانش‌آموزانی که کلاسِ سالِ جاری‌شان هنوز مشخص نیست. */
    List<PendingStudent> listPending(Long schoolId);

    /**
     * فارغ‌التحصیل‌شدگانِ اجرای امسال (برای تکرار پایه‌ی دستی): دانش‌آموزی که در آن اجرا GRADUATE گرفته و هنوز
     * GRADUATED است. {@code gradeLevel} همان پایه‌ای که تمام کرد.
     */
    List<PendingStudent> listGraduates(Long schoolId);

    /**
     * جا‌دادنِ دانش‌آموزانِ PENDING در کلاس‌های سالِ جاری (همه یا هیچ). کلاس باید پایه‌ی هدفِ دانش‌آموز را داشته باشد
     * (ClassGradeMismatch) و برای سالِ جاری یا «بدون سال» باشد (ClassNotInYear). تعداد جا‌داده‌شده را برمی‌گرداند.
     */
    int place(Long schoolId, List<Placement> placements);

    record UnattributedSummary(String currentYear, long classes, long students) {
    }

    record AttributionResult(String academicYear, int classes, int students) {
    }

    record PlanItem(Long studentId, Long userId, Long classId, int gradeLevel, PromotionOutcome defaultOutcome) {
    }

    /** {@code counts}: تعدادِ هر نتیجه؛ {@code undoable}: همین الان می‌توان آن را برگرداند. */
    record RunInfo(Long id, String fromYear, String toYear, String mode, Instant createdAt, Instant undoneAt,
                   Map<PromotionOutcome, Integer> counts, boolean undoable) {
    }

    /**
     * {@code gradeLevel}: پایه‌ی هدف (برای فارغ‌التحصیل: پایه‌ای که تمام کرد)؛ {@code previousGradeLevel}: پایه‌ی
     * سالِ قبل (تهی اگر معلوم نیست) - فرقِ این دو «ارتقا» را از «تکرار پایه» جدا می‌کند.
     */
    record PendingStudent(Long studentId, Long userId, int gradeLevel, Integer previousGradeLevel) {
    }

    /** {@code track}: رشته‌ی دانش‌آموز در سالِ جاری؛ تهی = بدون تغییر (ارتقا خودش رشته را کپی نمی‌کند). */
    record Placement(Long studentId, Long classId, String track) {
    }
}
