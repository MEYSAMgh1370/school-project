package com.example.roshdeandishe.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** فهرستِ کاملِ اقساطِ پرداخت‌نشده‌ی فاکتور با مبلغ و سررسیدِ تازه (تاریخ ISO: yyyy-MM-dd). */
public record UpdateInstallmentsRequest(List<Item> items) {

    public record Item(Long id, BigDecimal amount, LocalDate dueDate) {
    }
}
