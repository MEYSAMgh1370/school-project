package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.dto.MyEnrollmentResponse;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * «کلاس من چیست، در این مدرسه؟» - پشتیبان بخش خواندنی اپ موبایل دانش‌آموز
 * (thin read-only shell). عمداً یک کنترلر جداگانه از StudentController
 * است، نه یک متد اضافه به آن: StudentController کاملاً با
 * {@code @RequiresPermission("STUDENT_ENROLL"/"GUARDIAN_MANAGE")} گیت شده
 * (مدیریتیِ ادمین/معلم روی دانش‌آموزان مدرسه)، در حالی که این endpoint
 * دقیقاً همان مدل اعتماد {@code iam.MeController} را دارد: عمداً هیچ
 * {@code @RequiresPermission}/{@code @RequiresModule} ندارد، چون داده درباره‌ی
 * خودِ کاربر احراز هویت‌شده است (فقط StudentEntity متعلق به
 * iamFacade.currentUserId()، هرگز یک userId دلخواه از ورودی) - نه یک
 * قابلیت سطح مدرسه.
 * <p>
 * چرا این کنترلر در ماژول iam (کنار MeController) نیست: iam نمی‌تواند به
 * academic وابسته شود (academic از قبل به iam وابسته است - وابستگی برعکس
 * یعنی یک چرخه که ModularityTests.verifyModularStructure() آن را رد
 * می‌کند). این دقیقاً همان الگویی است که ماژول bale برای «به این کاربرِ
 * مشخص، درباره‌ی داده‌ی خودش چه می‌توان گفت» استفاده می‌کند (ترکیب IamFacade
 * + AcademicFacade، بدون اینکه هیچ‌کدام به bale وابسته شوند).
 * <p>
 * {@code schoolId} به‌عنوان query param، نه X-School-ID/path variable: یک
 * کاربر می‌تواند هم‌زمان STUDENT یک مدرسه و چیز دیگری در مدرسه‌ی دیگر باشد،
 * پس باید صراحتاً بگوید ثبت‌نامِ کدام مدرسه را می‌خواهد - به همین دلیل هم
 * TenantContext/X-School-ID اینجا درگیر نمی‌شود (این مسیر از قبل با پیشوند
 * "/api/me" در TenantResolverFilter مستثنا شده، دقیقاً مثل بقیه‌ی
 * /api/me/**).
 */
@RestController
public class MyEnrollmentController {

    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;

    public MyEnrollmentController(AcademicFacade academicFacade, IamFacade iamFacade) {
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/enrollment")
    public ResponseEntity<MyEnrollmentResponse> myEnrollment(@RequestParam Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        return academicFacade.findMyEnrollment(userId, schoolId)
                .map(info -> ResponseEntity.ok(MyEnrollmentResponse.from(info)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
