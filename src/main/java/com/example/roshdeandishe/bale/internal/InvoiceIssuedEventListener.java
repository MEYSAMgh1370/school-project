package com.example.roshdeandishe.bale.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.bale.BaleFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * اطلاع‌رسانی خروجی، اولین موردِ واقعیِ این ماژول: وقتی فاکتوری صادر
 * می‌شود، هم خودِ دانش‌آموز (اگر حسابش را لینک کرده) و هم همه‌ی ولی‌های
 * لینک‌شده‌اش پیام می‌گیرند. اگر هیچ‌کدام لینک نکرده باشند،
 * BaleFacade.sendMessageToUser فقط false برمی‌گرداند - بدون خطا، بدون اثر.
 * <p>
 * همان الگوی بازیابی TenantContext که در FinanceAuditEventListener/
 * StudentEnrolledEventListener استفاده شده، اینجا هم لازم است - این
 * Listener در Thread جدید اجرا می‌شود (CLAUDE.md §3: Thread-boundary rule).
 */
@Component
class InvoiceIssuedEventListener {

    private final AcademicFacade academicFacade;
    private final BaleFacade baleFacade;

    InvoiceIssuedEventListener(AcademicFacade academicFacade, BaleFacade baleFacade) {
        this.academicFacade = academicFacade;
        this.baleFacade = baleFacade;
    }

    @ApplicationModuleListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            StudentInfo student = academicFacade.getStudentInfo(event.studentId());
            String text = "فاکتور جدیدی به مبلغ " + event.amount() + " برای دانش‌آموز صادر شد.";

            List<Long> recipients = new ArrayList<>();
            recipients.add(student.userId());
            recipients.addAll(academicFacade.listGuardians(student.id().value()));

            for (Long recipientUserId : recipients) {
                baleFacade.sendMessageToUser(recipientUserId, text);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
