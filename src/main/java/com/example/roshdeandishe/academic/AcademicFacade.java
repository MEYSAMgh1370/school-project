package com.example.roshdeandishe.academic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    StudentInfo getStudentInfo(Long schoolId, Long studentId);

    /**
     * نسخه‌ی Optional از getStudentInfo برای فراخوان‌هایی که «نبودن
     * دانش‌آموز» برایشان حالت عادی است، نه خطای دامنه - مثل
     * notification.internal.InvoiceIssuedEventListener که برای فاکتوری با studentId بدون
     * StudentEntity فقط باید اطلاع‌رسانی را رد کند (همان قرارداد
     * findMyEnrollment/NotificationFacade.sendMessageToUser).
     */
    Optional<StudentInfo> findStudentInfo(Long studentId);

    List<StudentInfo> listStudents(Long schoolId);

    /**
     * gradeLevel باید در بازه‌ی مقطع تحصیلی همین مدرسه باشد (school.SchoolFacade.SchoolLevel،
     * مثلاً یک دبستان فقط پایه‌های ۱ تا ۶ را می‌پذیرد) - وگرنه
     * AcademicError.InvalidGradeLevel. مدرسه‌ای که schoolId اش اصلاً وجود
     * ندارد AcademicError.SchoolNotFound می‌دهد.
     * <p>
     * gradingScheme صریح است، نه مشتق‌شده در پایگاه‌داده - اما وقتی صدا زننده
     * null بدهد (CreateClassRequest)، پیش‌فرضش از روی
     * gradeLevel محاسبه می‌شود (۱ تا ۶ → DESCRIPTIVE، ۷ تا ۱۲ → NUMERIC، §11
     * Q8's reconciliation با classification که در PR جداگانه‌ای اضافه شد).
     */
    ClassId createClass(Long schoolId, String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme);

    List<ClassInfo> listClasses(Long schoolId);

    /**
     * ثبت/به‌روزرسانیِ نمره‌ی یک دانش‌آموز در یک کلاس برای یک نوبتِ مشخص.
     * Idempotent است: نمره‌ی همان (studentId, classId, academicYear, term) به‌جای
     * ثبتِ تکراری به‌روزرسانی می‌شود - همان الگوی setTuitionFee/assignPlanToStudent.
     * <p>
     * دانش‌آموز باید همین الان عضو همین کلاس باشد
     * (AcademicError.StudentNotInClass وگرنه)، و دقیقاً یکی از
     * descriptiveLevel/numericScore باید متناظر با شِمای واقعیِ کلاس
     * (ClassEntity.gradingScheme) پر باشد (AcademicError.GradingSchemeMismatch
     * وگرنه) - شِما پارامتر جداگانه نمی‌گیرد چون قابل استنتاج از خودِ کلاس است.
     * <p>
     * {@code courseId} اختیاری است: {@code null} یعنی «نمرهٔ کل» (رفتار پیشین)؛
     * با مقدار، درس باید در فهرست سراسری باشد (AcademicError.CourseNotFound) و
     * هم‌پایه‌ی کلاس (AcademicError.CourseNotForClass). برای هر (دانش‌آموز، کلاس،
     * سال، نوبت) یک نمرهٔ کل و یک نمره به‌ازای هر درس می‌تواند وجود داشته باشد.
     */
    GradeId recordGrade(Long schoolId, Long studentId, Long classId, String academicYear, GradingTerm term,
                         DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore, String comment,
                         Long courseId);

    /**
     * درس‌های سراسریِ هم‌پایه‌ی یک کلاس، به ترتیب نام - برای انتخابگر درس در
     * دفتر نمرات. فهرست فقط‌خواندنی است (openspec/changes/grades-by-course.md).
     * AcademicError.ClassNotFound اگر کلاس متعلق به این مدرسه نباشد.
     */
    List<CourseInfo> listCoursesForClass(Long schoolId, Long classId);

    GradeInfo getGrade(Long gradeId);

    /**
     * همه‌ی نمره‌های یک کلاس برای یک نوبتِ مشخص - نمای دفترچه‌نمره‌ی معلم
     * برای ورود/بازبینیِ نمرات. عمداً فقط با GRADES_EDIT گیت می‌شود، نه
     * GRADES_VIEW (که STUDENT/PARENT هم دارند) - وگرنه یک دانش‌آموز می‌توانست
     * با فراخوانی مستقیمِ این endpoint، نمره‌ی کل هم‌کلاسی‌هایش را ببیند.
     * {@code courseId == null} فقط «نمرهٔ کل»ها را برمی‌گرداند (همان رفتار پیشین)،
     * با مقدار فقط نمره‌های همان درس را.
     */
    List<GradeInfo> listGradesForClass(Long schoolId, Long classId, String academicYear, GradingTerm term,
                                       Long courseId);

    /**
     * همه‌ی نمره‌های ثبت‌شده برای چند دانش‌آموز مشخص، در همه‌ی سال‌ها/نوبت‌ها -
     * پشتیبانِ academic.MyGradesController (خودِ دانش‌آموز + فرزندانِ لینک‌شده‌ی
     * والد)، همان الگوی FinanceFacade.listInvoicesForStudents.
     */
    List<GradeInfo> listGradesForStudents(Long schoolId, List<Long> studentIds);

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
     * همه‌ی ولی‌های لینک‌شده به یک دانش‌آموز خاص، بعد از تأیید تعلق دانش‌آموز
     * به همان مدرسه.
     */
    List<Long> listGuardians(Long schoolId, Long studentId);

    /**
     * همه‌ی دانش‌آموزانی که این کاربر در این مدرسه ولیِ آن‌هاست - نقطه‌ی
     * ورودی اصلی برای «سرویس‌دهی خودکار» یک PARENT (مثلاً از طریق ربات
     * بله): فقط دانش‌آموزان واقعاً لینک‌شده برمی‌گردد، نه کل دانش‌آموزان
     * مدرسه.
     */
    List<StudentInfo> listWards(Long guardianUserId, Long schoolId);

    /**
     * ثبت‌نامِ خودِ کاربر در یک مدرسه‌ی خاص - برای endpoint خودسرویس
     * «کلاس من چیست» (academic.MyEnrollmentController، پشتیبان اپ موبایل
     * دانش‌آموز). Optional، نه throwing: «این کاربر در این مدرسه دانش‌آموز
     * نیست» یک حالت عادی است (مثلاً یک SCHOOL_ADMIN یا TEACHER که اصلاً
     * StudentEntity ندارد)، نه یک خطای دامنه - همان قرارداد
     * issueInviteToken/NotificationFacade.sendMessageToUser.
     */
    Optional<EnrollmentInfo> findMyEnrollment(Long userId, Long schoolId);

    record StudentId(Long value) {
    }

    record GuardianLinkId(Long value) {
    }

    record ClassId(Long value) {
    }

    record GradeId(Long value) {
    }

    record CourseId(Long value) {
    }

    record CourseInfo(CourseId id, int gradeLevel, String name) {
    }

    record StudentInfo(StudentId id, Long userId, Long schoolId, ClassId classId) {
    }

    record ClassInfo(ClassId id, Long schoolId, String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme) {
    }

    record GradeInfo(GradeId id, Long schoolId, StudentId studentId, ClassId classId, String academicYear,
                      GradingTerm term, GradingScheme scheme, DescriptiveGradeLevel descriptiveLevel,
                      BigDecimal numericScore, String comment, Long recordedByUserId, Instant recordedAt,
                      Long courseId, String courseName) {
    }

    record EnrollmentInfo(StudentId studentId, Long schoolId, ClassId classId, String className, String section) {
    }
}
