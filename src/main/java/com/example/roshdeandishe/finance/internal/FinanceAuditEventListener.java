package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * هر InvoiceIssuedEvent را به یک ردیف {@link FinanceAuditEntity} تبدیل
 * می‌کند. جدا از FinanceFacadeImpl نگه داشته شده تا صدور فاکتور و ثبت audit
 * دو مسئولیت مجزا بمانند - اگر روزی نوشتن audit کند/ناموفق شود، تراکنش
 * اصلی صدور فاکتور (که قبلاً commit شده) تحت تأثیر قرار نمی‌گیرد.
 * <p>
 * همان الگوی بازیابی TenantContext که در StudentEnrolledEventListener
 * استفاده شده، اینجا هم لازم است - این Listener در Thread جدید اجرا می‌شود.
 */
@Component
class FinanceAuditEventListener {

    private final FinanceAuditRepository financeAuditRepository;

    FinanceAuditEventListener(FinanceAuditRepository financeAuditRepository) {
        this.financeAuditRepository = financeAuditRepository;
    }

    @ApplicationModuleListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            financeAuditRepository.save(new FinanceAuditEntity(
                    event.schoolId(),
                    event.actorUserId(),
                    "INVOICE_ISSUED",
                    event.studentId(),
                    event.amount(),
                    event.occurredAt()
            ));
        } finally {
            TenantContext.clear();
        }
    }
}
