package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.platform.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * اطلاع‌رسانی خروجی، اولین موردِ واقعیِ این ماژول: وقتی فاکتوری صادر
 * می‌شود، هم خودِ دانش‌آموز (اگر حسابش را لینک کرده) و هم همه‌ی ولی‌های
 * لینک‌شده‌اش پیام می‌گیرند. اگر هیچ‌کدام لینک نکرده باشند،
 * NotificationFacade.sendMessageToUser فقط false برمی‌گرداند - بدون خطا، بدون اثر.
 * <p>
 * همان الگوی بازیابی TenantContext که در FinanceAuditEventListener/
 * StudentEnrolledEventListener استفاده شده، اینجا هم لازم است - این
 * Listener در Thread جدید اجرا می‌شود (CLAUDE.md §3: Thread-boundary rule).
 */
@Component
class InvoiceIssuedEventListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceIssuedEventListener.class);

    private final AcademicFacade academicFacade;
    private final NotificationFacade notificationFacade;

    InvoiceIssuedEventListener(AcademicFacade academicFacade, NotificationFacade notificationFacade) {
        this.academicFacade = academicFacade;
        this.notificationFacade = notificationFacade;
    }

    @ApplicationModuleListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            // نبودن StudentEntity برای این studentId یک حالت عادی است (مثلاً
            // فاکتوری که مستقیم با issueTuitionInvoice صادر شده)، نه خطا -
            // همان قرارداد sendMessageToUser: فقط اطلاع‌رسانی رد می‌شود.
            Optional<StudentInfo> found = academicFacade.findStudentInfo(event.studentId());
            if (found.isEmpty()) {
                log.warn("اطلاع‌رسانی فاکتور رد شد: دانش‌آموزی با شناسه {} در مدرسه {} وجود ندارد",
                        event.studentId(), event.schoolId());
                return;
            }
            StudentInfo student = found.get();
            String text = "فاکتور جدیدی به مبلغ " + event.amount() + " برای دانش‌آموز صادر شد.";

            List<Long> recipients = new ArrayList<>();
            recipients.add(student.userId());
            recipients.addAll(academicFacade.listGuardians(student.schoolId(), student.id().value()));

            for (Long recipientUserId : recipients) {
                notificationFacade.sendMessageToUser(recipientUserId, event.schoolId(), text);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
