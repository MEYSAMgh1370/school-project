package com.example.roshdeandishe.school.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * مدیریت اکسپشن‌های مختص ماژول school. نام Bean صریح است (مطابق همان
 * قراردادی که برای IamExceptionHandler/AcademicExceptionHandler استفاده شد)
 * تا تداخل نام Bean بین ماژول‌ها رخ ندهد.
 * <p>
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
@Component("schoolExceptionHandler")
@Order(0)
public class SchoolExceptionHandler {

    @ExceptionHandler(SchoolException.class)
    public ProblemDetail handleSchoolException(SchoolException ex) {
        return switch (ex.error()) {
            case SchoolError.SchoolNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "school-not-found",
                    "مدرسه یافت نشد",
                    "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد."
            );
            case SchoolError.InvalidSlug e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-slug",
                    "نشانی اختصاصی نامعتبر است",
                    "نشانی «" + e.slug() + "» مجاز نیست. فقط حروف کوچک انگلیسی، رقم و خط تیره (نه در ابتدا/انتها)، بین ۳ تا ۶۳ نویسه."
            );
            case SchoolError.SlugAlreadyTaken e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "slug-already-taken",
                    "نشانی اختصاصی قبلاً گرفته شده",
                    "نشانی «" + e.slug() + "» قبلاً توسط مدرسه‌ی دیگری ثبت شده است. نشانی دیگری انتخاب کنید."
            );
            case SchoolError.AcademicYearNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "academic-year-not-found",
                    "سال تحصیلی یافت نشد",
                    "سال تحصیلی‌ای با شناسه " + e.academicYearId() + " در فهرست سال‌ها نیست."
            );
        };
    }
}
