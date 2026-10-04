package com.example.roshdeandishe.academic.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @Order(0)}: باید قبل از {@code PlatformExceptionHandler}
 * ({@code @Order(Integer.MAX_VALUE)}) اجرا شود، وگرنه هر دو روی مقدار
 * پیش‌فرض {@code Ordered.LOWEST_PRECEDENCE} تساوی می‌خورند و نتیجه به
 * ترتیب نامعین ثبت beanها بستگی پیدا می‌کند.
 * <p>
 * ⚠️ نام Bean از طریق {@code @Component("...")} تنظیم شده، نه
 * {@code @RestControllerAdvice("...")}. {@code value()} در
 * {@code @ControllerAdvice} با {@code @AliasFor("basePackages")} به
 * {@code basePackages} (یک {@code String[]}) نگاشت می‌شود - پاس دادن رشته
 * به آن، Advice را به کنترلرهای زیرِ پکیجی به همان نام محدود می‌کند (که
 * وجود ندارد)، و این handler را برای همیشه غیرقابل‌اعمال بر هر کنترلر
 * واقعی می‌کند، صرف‌نظر از @Order. تأیید شده با اجرای واقعی HTTP.
 */
@RestControllerAdvice
@Component("academicGlobalExceptionHandler")
@Order(0)
public class AcademicExceptionHandler {

    @ExceptionHandler(AcademicException.class)
    public ProblemDetail handleAcademicException(AcademicException ex) {
        return switch (ex.error()) {
            case AcademicError.StudentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "student-not-found",
                    "دانش‌آموز یافت نشد",
                    "دانش‌آموزی با شناسه " + e.studentId() + " یافت نشد."
            );
            case AcademicError.ClassNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "class-not-found",
                    "کلاس یافت نشد",
                    "کلاسی با شناسه " + e.classId() + " یافت نشد."
            );
            case AcademicError.GuardianNotEligible e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "guardian-not-eligible",
                    "کاربر واجد شرایط ولی نیست",
                    "کاربر " + e.guardianUserId() + " عضویت ولیِ فعالی در این مدرسه ندارد."
            );
            case AcademicError.StudentNotInClass e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "student-not-in-class",
                    "دانش‌آموز عضو این کلاس نیست",
                    "دانش‌آموز " + e.studentId() + " در حال حاضر عضو کلاس " + e.classId() + " نیست."
            );
            case AcademicError.GradingSchemeMismatch e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "grading-scheme-mismatch",
                    "عدم تطابق با شِمای نمره‌دهی",
                    "این کلاس شِمای " + e.expectedScheme() + " دارد؛ فیلد متناظر با همین شِما را پر کنید."
            );
            case AcademicError.InvalidGrade e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-grade",
                    "نمره نامعتبر است",
                    e.reason()
            );
            case AcademicError.GradeNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "grade-not-found",
                    "نمره یافت نشد",
                    "نمره‌ای با شناسه " + e.gradeId() + " یافت نشد."
            );
            case AcademicError.CourseNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "course-not-found",
                    "درس یافت نشد",
                    "درسی با شناسه " + e.courseId() + " یافت نشد."
            );
            case AcademicError.CourseNotForClass e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "course-not-for-class",
                    "درس مربوط به پایه‌ی این کلاس نیست",
                    "درس " + e.courseId() + " برای پایه‌ی کلاس " + e.classId() + " تعریف نشده است."
            );
            case AcademicError.AcademicYearNotAllowed e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "academic-year-not-allowed",
                    "دسترسی به این سال تحصیلی مجاز نیست",
                    "فقط مدیر مدرسه سال‌های غیرجاری را می‌بیند؛ سالِ «" + e.academicYear() + "» جاری نیست."
            );
            case AcademicError.CourseNotValidForYear e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "course-not-valid-for-year",
                    "این درس در این سال تحصیلی ارائه نمی‌شود",
                    "درس " + e.courseId() + " برای سال «" + e.academicYear() + "» معتبر نیست."
            );
            case AcademicError.CourseNotGradable e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "course-not-gradable",
                    "این درس در کارنامه نمی‌آید",
                    "درس " + e.courseId() + " فقط در برنامهٔ کلاسی هست و نمره‌پذیر نیست."
            );
            case AcademicError.InvalidAcademicYear e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-academic-year",
                    "سال تحصیلی نامعتبر است",
                    "سال تحصیلی «" + e.academicYear() + "» در فهرست سال‌ها نیست."
            );
            case AcademicError.YearNotChosen e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "year-not-chosen",
                    "سال تحصیلی جاری انتخاب نشده است",
                    "اول در صفحه‌ی «سال تحصیلی» سالِ جاری مدرسه را انتخاب کنید."
            );
            case AcademicError.InvalidRollover e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-rollover",
                    "درخواست ارتقا یا انتساب نامعتبر است",
                    e.reason()
            );
            case AcademicError.ClassNotInYear e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "class-not-in-year",
                    "کلاس برای سال دیگری است",
                    "کلاس " + e.classId() + " برای سال «" + e.classYear() + "» است؛ کلاسی از سال «" + e.expectedYear() + "» انتخاب کنید."
            );
            case AcademicError.ClassGradeMismatch e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "class-grade-mismatch",
                    "پایه‌ی کلاس با پایه‌ی دانش‌آموز نمی‌خواند",
                    "کلاس " + e.classId() + " پایه‌ی " + e.classGrade() + " است ولی این دانش‌آموز پایه‌ی " + e.expectedGrade() + " می‌شود."
            );
            case AcademicError.GradeYearNotClassYear e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "grade-year-not-class-year",
                    "سالِ نمره با سالِ کلاس یکی نیست",
                    "کلاس " + e.classId() + " برای سال «" + e.classYear() + "» است؛ نمره‌ی سالِ «" + e.gradeYear() + "» در آن ثبت نمی‌شود."
            );
            case AcademicError.PromotionRunNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "promotion-run-not-found",
                    "اجرای ارتقا یافت نشد",
                    "اجرای ارتقای " + e.runId() + " یافت نشد."
            );
            case AcademicError.PromotionRunNotUndoable e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "promotion-run-not-undoable",
                    "این اجرای ارتقا برگشت‌پذیر نیست",
                    e.reason()
            );
            case AcademicError.PeriodNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "period-not-found",
                    "زنگ یافت نشد",
                    "زنگی با شناسه " + e.periodId() + " یافت نشد."
            );
            case AcademicError.InvalidPeriod e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-period",
                    "زنگ نامعتبر است",
                    e.reason()
            );
            case AcademicError.PeriodOverlaps e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "period-overlaps",
                    "زنگ هم‌پوشانی دارد",
                    "این زنگ با زنگ " + e.otherPeriodId() + " همان مدرسه و سال هم‌پوشانی دارد."
            );
            case AcademicError.PeriodHasEntries e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "period-has-entries",
                    "زنگ در برنامه به کار رفته است",
                    "زنگ " + e.periodId() + " خانه‌ی برنامه دارد؛ اول آن خانه‌ها را خالی کنید."
            );
            case AcademicError.ActivityNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "activity-not-found",
                    "فعالیت یافت نشد",
                    "فعالیتی با شناسه " + e.activityId() + " یافت نشد."
            );
            case AcademicError.ActivityNameTaken e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "activity-name-taken",
                    "نام فعالیت تکراری است",
                    "فعالیتی با نام «" + e.name() + "» از قبل هست."
            );
            case AcademicError.ActivityNotEditable e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "activity-not-editable",
                    "فعالیتِ پیش‌فرضِ پلتفرم قابل تغییر نیست",
                    "فعالیت " + e.activityId() + " پیش‌فرضِ پلتفرم است؛ فقط فعالیت‌های خودِ مدرسه قابل تغییرند."
            );
            case AcademicError.ActivityInUse e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "activity-in-use",
                    "فعالیت در برنامه به کار رفته است",
                    "فعالیت " + e.activityId() + " در برنامه هست؛ اول آن خانه‌ها را خالی کنید."
            );
            case AcademicError.InvalidTimetableEntry e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-timetable-entry",
                    "خانه‌ی برنامه نامعتبر است",
                    e.reason()
            );
            case AcademicError.TeacherNotEligible e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "teacher-not-eligible",
                    "کاربر معلمِ فعالِ این مدرسه نیست",
                    "کاربر " + e.teacherUserId() + " عضویت معلمِ فعالی در این مدرسه ندارد."
            );
            case AcademicError.TeacherDoubleBooked e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "teacher-double-booked",
                    "معلم در همین زمان کلاس دیگری دارد",
                    "این معلم در همین روز و زنگ در کلاس دیگری حضور دارد."
            );
            case AcademicError.SchoolNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "school-not-found",
                    "مدرسه یافت نشد",
                    "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد."
            );
            case AcademicError.InvalidGradeLevel e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-grade-level",
                    "پایه‌ی تحصیلی نامعتبر",
                    "پایه‌ی " + e.gradeLevel() + " در بازه‌ی مقطع تحصیلی این مدرسه ("
                            + e.minGrade() + " تا " + e.maxGrade() + ") نیست."
            );
        };
    }
}
