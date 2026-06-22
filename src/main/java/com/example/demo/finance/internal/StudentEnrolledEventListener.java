package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.academic.StudentEnrolledEvent;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.shared.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * نمونه‌ی عملی الگوی "Event-Carried State Transfer" بین ماژول‌ها.
 * <p>
 * {@code @ApplicationModuleListener} معادل ترکیب {@code @Async} +
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} است؛ یعنی این
 * Listener فقط بعد از کامیت موفق تراکنش ثبت‌نام در ماژول academic، و در یک
 * Thread/Transaction جداگانه اجرا می‌شود.
 * <p>
 * چون این کد در یک Thread جدید اجرا می‌شود، {@link TenantContext} خالی است؛
 * بنابراین schoolId را از خود رویداد (نه از TenantContext) می‌خوانیم و
 * در ابتدای متد دوباره در TenantContext قرار می‌دهیم تا منطق پایین‌دستی
 * (مثل Repositoryهای finance که ممکن است بر اساس schoolId فیلتر شوند) درست کار کند.
 */
@Component
class StudentEnrolledEventListener {

    private final FinanceFacade financeFacade;

    StudentEnrolledEventListener(FinanceFacade financeFacade) {
        this.financeFacade = financeFacade;
    }

    @ApplicationModuleListener
    void onStudentEnrolled(StudentEnrolledEvent event) {
        try {
            // بازیابی Tenant Context در این Thread جدید، از روی payload رویداد
            TenantContext.set(event.schoolId());

            // TODO: مقدار amount باید از Pricing/Tariff واقعی مدرسه خوانده شود
            financeFacade.issueTuitionInvoice(
                    event.schoolId(),
                    event.studentId(),
                    BigDecimal.valueOf(0)
            );
        } finally {
            // پاک‌سازی، چون این Thread ممکن است بعداً برای Job دیگری reuse شود
            TenantContext.clear();
        }
    }
}
