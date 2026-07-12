package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.ExpenseStatusChangedEvent;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.finance.PaymentRecordedEvent;
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

    @ApplicationModuleListener
    void onPaymentRecorded(PaymentRecordedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            financeAuditRepository.save(new FinanceAuditEntity(
                    event.schoolId(),
                    event.actorUserId(),
                    "PAYMENT_RECORDED",
                    event.studentId(),
                    event.amount(),
                    event.occurredAt()
            ));
        } finally {
            TenantContext.clear();
        }
    }

    @ApplicationModuleListener
    void onExpenseStatusChanged(ExpenseStatusChangedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            // previousStatus == null یعنی ایجاد (→ DRAFT)؛ بقیه از newStatus
            String action = event.previousStatus() == null ? "EXPENSE_RECORDED" : switch (event.newStatus()) {
                case APPROVED -> "EXPENSE_APPROVED";
                case PAID -> "EXPENSE_PAID";
                case CANCELLED -> "EXPENSE_CANCELLED";
                case DRAFT -> "EXPENSE_RECORDED";
            };

            financeAuditRepository.save(FinanceAuditEntity.forExpense(
                    event.schoolId(),
                    event.actorUserId(),
                    action,
                    event.expenseId(),
                    event.amount(),
                    event.occurredAt()
            ));
        } finally {
            TenantContext.clear();
        }
    }
}
