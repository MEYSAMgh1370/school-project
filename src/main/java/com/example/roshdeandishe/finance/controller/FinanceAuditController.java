package com.example.roshdeandishe.finance.controller;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.AuditPage;
import com.example.roshdeandishe.finance.FinanceFacade.AuditQuery;
import com.example.roshdeandishe.finance.dto.response.AuditEntryResponse;
import com.example.roshdeandishe.finance.dto.response.AuditPageResponse;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** خواندنِ audit trail مالی برای ادمین مدرسه (فقط‌خواندنی؛ هیچ مسیرِ نوشتن/حذف ندارد). */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/finance/audit")
@RequiresModule("FINANCE")
public class FinanceAuditController {

    private final FinanceFacade financeFacade;

    public FinanceAuditController(FinanceFacade financeFacade) {
        this.financeFacade = financeFacade;
    }

    @GetMapping
    @RequiresPermission("FINANCE_MANAGE")
    public AuditPageResponse list(
            @PathVariable Long schoolId,
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        TenantContext.requireMatches(schoolId);

        AuditPage result = financeFacade.listAuditTrail(schoolId,
                new AuditQuery(invoiceId, studentId, action, parseDate("from", from), parseDate("to", to), page, size));
        return new AuditPageResponse(result.page(), result.size(), result.totalItems(), result.items().stream()
                .map(e -> new AuditEntryResponse(e.id(), e.occurredAt(), e.action(), e.actorUserId(), e.actorName(),
                        e.studentId(), e.studentName(), e.invoiceId(), e.expenseId(), e.amount(), e.details()))
                .toList());
    }

    private static LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new FinanceException(new FinanceError.InvalidAuditFilter(
                    "پارامتر " + name + " باید تاریخی به شکل yyyy-MM-dd باشد."));
        }
    }
}
