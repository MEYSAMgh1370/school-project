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

    /**
     * همان ثبت‌نام با رشته‌ی دانش‌آموز ({@code ریاضی}، {@code تجربی} یا {@code انسانی}) روی ردیفِ ثبت‌نامِ سالِ جاری.
     * {@code track} تهی = رشته دست نمی‌خورد (برای ردیفِ تازه تهی می‌ماند). رشته‌ی نامعتبر InvalidTrack؛ رشته برای مدرسه‌ای
     * که سالِ جاری ندارد (ردیفی نیست که ذخیره شود) TrackNeedsCurrentYear و هیچ اثری نمی‌گذارد.
     */
    StudentId enrollStudent(String email, String fullName, Long schoolId, Long classId, String track);

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

    /**
     * دانش‌آموزانِ فعالِ مدرسه ({@code ACTIVE} و {@code PENDING}؛ فارغ‌التحصیل و ترک‌کرده نه).
     * {@link #findStudentInfo} هر وضعیتی را می‌دهد.
     */
    List<StudentInfo> listStudents(Long schoolId);

    /**
     * دانش‌آموزانِ یک کلاس: کسانی که همین الان در آن‌اند، به‌علاوه‌ی کسانی که در سالی در آن ثبت‌نام داشته‌اند
     * (با هر وضعیتِ فعلی) - برای دفتر نمراتِ کلاسِ یک سالِ قبل (academic-year.md فاز ۳). ClassNotFound اگر
     * کلاس از این مدرسه نباشد.
     */
    List<StudentInfo> listStudentsInClass(Long schoolId, Long classId);

    /**
     * دانش‌آموزانِ یک کلاس برای یک سال: کسانی که همین الان در آن‌اند، به‌علاوه‌ی کسانی که در همان سال در آن ثبت‌نام
     * داشته‌اند. این همان قاعده‌ای است که ثبتِ نمره با آن می‌سنجد (recordGrade)، پس هرکس فهرست شود نمره‌اش ثبت می‌شود.
     * کسی که کلاس را ترک کرده و در این سال در آن کلاس نبوده، فهرست نمی‌شود.
     */
    List<StudentInfo> listStudentsInClass(Long schoolId, Long classId, String academicYear);

    /**
     * دانش‌آموزانی که در یک سال ثبت‌نام‌اند - مبنای «صدور فاکتورهای آن سال» (academic-year.md فاز ۳). در مدرسه‌ی
     * «ردیاب‌شده» (سالِ جاری دارد و همه‌ی دانش‌آموزانش ردیفِ ثبت‌نام دارند) همه‌ی کسانی که در آن سال ردیف دارند
     * (ENROLLED/PENDING، با هر وضعیتِ فعلی)؛ در هر مدرسه‌ی دیگر همه‌ی دانش‌آموزانِ فعال، مثل پیش از این.
     * سالِ ناشناخته: InvalidAcademicYear.
     */
    List<StudentInfo> listStudentsEnrolledIn(Long schoolId, String academicYear);

    /**
     * شرطِ نسبت دادنِ برنامه‌ی مطالعه یا آزمون به یک دانش‌آموز: او باید در سالِ تحصیلیِ حاوی {@code date} ردیفِ ثبت‌نام
     * داشته باشد (ENROLLED یا PENDING؛ هر ردیفی کافی است). سالِ حاوی تاریخ همان سالی از
     * {@code SchoolFacade.listAcademicYears()} است که {@code startDate <= date <= endDate}؛ هیچ سال یا پایه‌ای حدس
     * زده نمی‌شود.
     * <p>
     * فقط برای مدرسه‌ای که سالِ جاری دارد اعمال می‌شود؛ مدرسه‌ی بدونِ سالِ جاری مثل پیش از این بدون بررسی و بدون خطا
     * می‌گذرد. اگر مدرسه سالِ جاری دارد ولی هیچ سالی تاریخ را در بر نمی‌گیرد AcademicError.NoAcademicYearForDate؛
     * اگر دانش‌آموز در آن سال ردیفی ندارد AcademicError.StudentNotEnrolledInYear. هیچ اثری نمی‌گذارد.
     */
    void requireEnrolledOn(Long schoolId, Long studentId, java.time.LocalDate date);

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

    /**
     * مثل {@link #createClass(Long, String, Long, String, int, GradingScheme)} ولی برای یک سالِ مشخص
     * (academic-year.md فاز ۳). {@code academicYear} تهی = سالِ جاریِ مدرسه، و اگر مدرسه سالِ جاری ندارد کلاس
     * «بدون سال» ساخته می‌شود (رفتار پیشین). سالِ ناشناخته: InvalidAcademicYear.
     */
    ClassId createClass(Long schoolId, String name, Long teacherId, String section, int gradeLevel,
                        GradingScheme gradingScheme, String academicYear);

    /** همه‌ی کلاس‌های مدرسه در همه‌ی سال‌ها. */
    List<ClassInfo> listClasses(Long schoolId);

    /**
     * کلاس‌های یک سال به‌علاوه‌ی کلاس‌های «بدون سال» (هنوز به سالی نسبت داده نشده‌اند). سالِ ناشناخته:
     * InvalidAcademicYear.
     */
    List<ClassInfo> listClasses(Long schoolId, String academicYear);

    /**
     * یک کلاسِ مدرسه با شناسه؛ خالی اگر کلاس نباشد یا از مدرسه‌ی دیگری باشد (وجودش لو نمی‌رود) -
     * پشتوانه‌ی حضور و غیاب (openspec/changes/attendance.md).
     */
    Optional<ClassInfo> findClass(Long schoolId, Long classId);

    /**
     * دانش‌آموزانِ چند کلاس با یک بار خواندن، با همان معنای {@link #listStudentsInClass} (اکنون در کلاس‌اند یا
     * در آن ثبت‌نام داشته‌اند، با هر وضعیتِ فعلی). کلاسی که از این مدرسه نباشد کلیدِ خالی دارد، نه خطا.
     */
    java.util.Map<Long, List<StudentInfo>> listStudentsByClass(Long schoolId, java.util.Collection<Long> classIds);

    /**
     * ثبت/به‌روزرسانیِ نمره‌ی یک دانش‌آموز در یک کلاس برای یک نوبتِ مشخص.
     * Idempotent است: نمره‌ی همان (studentId, classId, academicYear, term) به‌جای
     * ثبتِ تکراری به‌روزرسانی می‌شود - همان الگوی setTuitionFee/assignPlanToStudent.
     * <p>
     * دانش‌آموز باید همین الان عضو همین کلاس باشد، یا در همان سالِ نمره در آن کلاس
     * ثبت‌نام داشته باشد (اصلاحِ نمره‌ی سالِ قبل در کلاسِ قبلی؛ AcademicError.StudentNotInClass
     * وگرنه). سالِ نمره باید در فهرست سال‌ها باشد (InvalidAcademicYear) ولی با سالِ خودِ کلاس
     * هم‌خوانی لازم ندارد - نمره‌ها کلاسی‌اند (academic-year.md، تصمیم ۲۰۲۶-۱۰-۰۴). دقیقاً یکی از
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
     * دفتر نمرات (فقط درس‌های نمره‌پذیر: درسِ {@code graded = false} فقط در برنامهٔ
     * کلاسی می‌آید). فهرست فقط‌خواندنی است (openspec/changes/grades-by-course.md).
     * {@code academicYear} (اختیاری) فقط درس‌هایی را می‌دهد که برای آن سال ارائه می‌شوند
     * (بازه‌ی اعتبار، academic-year.md فاز ۱)؛ سالِ ناشناخته: InvalidAcademicYear.
     * AcademicError.ClassNotFound اگر کلاس متعلق به این مدرسه نباشد.
     */
    List<CourseInfo> listCoursesForClass(Long schoolId, Long classId, String academicYear);

    /** همه‌ی درس‌های پایه، بدون فیلترِ سال (بازه‌ی اعتبار نادیده). */
    default List<CourseInfo> listCoursesForClass(Long schoolId, Long classId) {
        return listCoursesForClass(schoolId, classId, null);
    }

    /**
     * درس‌های یک دانش‌آموز: درس‌های نمره‌پذیرِ پایه‌ی ثبت‌نامِ او، به ترتیب نام. پایه و رشته از ردیفِ ثبت‌نامِ
     * همان دانش‌آموز در {@code academicYear} خوانده می‌شود؛ تهی = سالِ جاریِ مدرسه. درسِ بدون رشته (مشترک) به همه‌ی
     * دانش‌آموزان پایه نشان داده می‌شود و درسِ رشته‌ای فقط به دانش‌آموزِ همان رشته. تهی بودنِ رشته‌ی دانش‌آموز
     * «همهٔ رشته‌ها» نیست: او فقط درس‌های مشترک را می‌بیند. بازه‌ی اعتبارِ درس برای همان سال اعمال می‌شود (مثل
     * {@link #listCoursesForClass}). فقط این فهرستِ دانش‌آموز بر اساس رشته فیلتر می‌شود؛
     * {@link #listCoursesForClass} و فهرست‌های معلم/بانک رشته را نمی‌شناسند و بدون فیلتر می‌مانند.
     * <p>
     * اگر دانش‌آموز در آن سال ردیفِ ثبت‌نام ندارد (یا مدرسه سالِ جاری ندارد و سالی داده نشده)
     * AcademicError.StudentNotEnrolledInYear؛ سالِ ناشناخته InvalidAcademicYear؛
     * دانش‌آموزی که از این مدرسه نباشد StudentNotFound.
     */
    List<CourseInfo> listCoursesForStudent(Long schoolId, Long studentId, String academicYear);

    /** مثل {@link #listCoursesForStudent(Long, Long, String)} برای سالِ جاریِ مدرسه. */
    List<CourseInfo> listCoursesForStudent(Long schoolId, Long studentId);

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
     * <p>
     * دانش‌آموزِ بدونِ کلاس (PENDING، GRADUATED) هم برمی‌گردد - با {@code classId} تهی - تا خودخدمتی
     * (فاکتورِ سالِ قبل، ...) کار کند؛ فقط {@code LEFT} خالی است.
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

    /**
     * {@code classId} همان «ثبت‌نامِ جاری» است و برای دانش‌آموزِ {@code PENDING}/{@code GRADUATED}/{@code LEFT}
     * تهی است (academic-year.md فاز ۳).
     */
    record StudentInfo(StudentId id, Long userId, Long schoolId, ClassId classId, StudentStatus status) {

        public StudentInfo(StudentId id, Long userId, Long schoolId, ClassId classId) {
            this(id, userId, schoolId, classId, StudentStatus.ACTIVE);
        }
    }

    /** {@code academicYear} تهی = به سالی نسبت داده نشده. */
    record ClassInfo(ClassId id, Long schoolId, String name, Long teacherId, String section, int gradeLevel,
                     GradingScheme gradingScheme, String academicYear) {

        public ClassInfo(ClassId id, Long schoolId, String name, Long teacherId, String section, int gradeLevel,
                         GradingScheme gradingScheme) {
            this(id, schoolId, name, teacherId, section, gradeLevel, gradingScheme, null);
        }
    }

    record GradeInfo(GradeId id, Long schoolId, StudentId studentId, ClassId classId, String academicYear,
                      GradingTerm term, GradingScheme scheme, DescriptiveGradeLevel descriptiveLevel,
                      BigDecimal numericScore, String comment, Long recordedByUserId, Instant recordedAt,
                      Long courseId, String courseName) {
    }

    /**
     * {@code classId}/{@code className}/{@code section} برای دانش‌آموزِ بدون کلاس (PENDING یا GRADUATED) تهی است.
     */
    record EnrollmentInfo(StudentId studentId, Long schoolId, ClassId classId, String className, String section,
                          StudentStatus status) {

        public EnrollmentInfo(StudentId studentId, Long schoolId, ClassId classId, String className, String section) {
            this(studentId, schoolId, classId, className, section, StudentStatus.ACTIVE);
        }
    }
}
