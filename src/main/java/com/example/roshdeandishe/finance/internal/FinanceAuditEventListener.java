package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.DiscountEarnedEvent;
import com.example.roshdeandishe.finance.DiscountLapsedEvent;
import com.example.roshdeandishe.finance.ExpenseStatusChangedEvent;
import com.example.roshdeandishe.finance.InvoiceDiscountedEvent;
import com.example.roshdeandishe.finance.InstallmentSnapshot;
import com.example.roshdeandishe.finance.InstallmentsChangedEvent;
import com.example.roshdeandishe.finance.InstallmentsSettingChangedEvent;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.finance.PaymentRecordedEvent;
import com.example.roshdeandishe.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_TEXT = 512;

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
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentId", event.paymentId());
        details.put("method", event.method() == null ? null : event.method().name());
        details.put("note", truncate(event.note()));
        saveInvoiceAudit(event.schoolId(), event.actorUserId(), "PAYMENT_RECORDED", event.studentId(),
                event.invoiceId(), event.amount(), details, event.occurredAt());
    }

    @ApplicationModuleListener
    void onInvoiceDiscounted(InvoiceDiscountedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("discountId", event.discountId());
        details.put("reason", truncate(event.reason()));
        details.put("deadline", event.deadline() == null ? null : event.deadline().toString());
        details.put("status", event.status() == null ? null : event.status().name());
        saveInvoiceAudit(event.schoolId(), event.actorUserId(), "INVOICE_DISCOUNTED", event.studentId(),
                event.invoiceId(), event.amount(), details, event.occurredAt());
    }

    @ApplicationModuleListener
    void onDiscountEarned(DiscountEarnedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("discountId", event.discountId());
        details.put("deadline", event.deadline() == null ? null : event.deadline().toString());
        details.put("trigger", event.trigger());
        saveInvoiceAudit(event.schoolId(), null, "DISCOUNT_EARNED", event.studentId(),
                event.invoiceId(), event.amount(), details, event.occurredAt());
    }

    @ApplicationModuleListener
    void onDiscountLapsed(DiscountLapsedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("discountId", event.discountId());
        details.put("deadline", event.deadline() == null ? null : event.deadline().toString());
        details.put("trigger", event.trigger());
        saveInvoiceAudit(event.schoolId(), null, "DISCOUNT_LAPSED", event.studentId(),
                event.invoiceId(), event.amount(), details, event.occurredAt());
    }

    @ApplicationModuleListener
    void onInstallmentsChanged(InstallmentsChangedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cause", event.cause().name());
        details.put("before", snapshots(event.before()));
        details.put("after", snapshots(event.after()));
        // ستون amount = جمعِ مبلغِ اقساطِ پس از تغییر
        BigDecimal total = event.after().stream().map(InstallmentSnapshot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        saveInvoiceAudit(event.schoolId(), event.actorUserId(), "INSTALLMENTS_CHANGED", event.studentId(),
                event.invoiceId(), total, details, event.occurredAt());
    }

    @ApplicationModuleListener
    void onInstallmentsSettingChanged(InstallmentsSettingChangedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", event.previousEnabled());
        details.put("to", event.enabled());
        saveInvoiceAudit(event.schoolId(), event.actorUserId(), "INSTALLMENTS_SETTING_CHANGED", null,
                null, null, details, event.occurredAt());
    }

    private void saveInvoiceAudit(Long schoolId, Long actorUserId, String action, Long studentId, Long invoiceId,
                                  BigDecimal amount, Map<String, Object> details, Instant occurredAt) {
        try {
            TenantContext.set(schoolId);

            financeAuditRepository.save(FinanceAuditEntity.forInvoice(
                    schoolId, actorUserId, action, studentId, invoiceId, amount, toJson(details), occurredAt));
        } finally {
            TenantContext.clear();
        }
    }

    private static List<Map<String, Object>> snapshots(List<InstallmentSnapshot> list) {
        return list.stream().map(snapshot -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("amount", snapshot.amount());
            item.put("amountPaid", snapshot.amountPaid());
            item.put("dueDate", snapshot.dueDate() == null ? null : snapshot.dueDate().toString());
            return item;
        }).toList();
    }

    /** جزئیات همیشه از Map ساخته و با Jackson سریال می‌شود؛ هرگز الحاقِ رشته‌ای نیست. */
    private static String toJson(Map<String, Object> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ساخت جزئیات audit شکست خورد", e);
        }
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
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
