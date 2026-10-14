package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی «سابقه‌ی تغییرات مالی» - همان الگوی shell نازکِ FinancePageController: فقط ناوبار
 * مشترک را می‌سازد؛ فیلتر، جدول و صفحه‌بندی کاملاً client-side و از طریق
 * {@code GET /api/admin/schools/{schoolId}/finance/audit} (ماژول FINANCE + مجوز FINANCE_MANAGE)
 * انجام می‌شود. صفحه فقط‌خواندنی است. فیلترهای {@code invoiceId} و {@code studentId} از query string
 * پیش‌پر می‌شوند (لینک «سابقه‌ی این فاکتور» در صفحه‌ی مالی).
 */
@Controller
public class FinanceAuditPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public FinanceAuditPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/finance-audit")
    public String financeAuditPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "finance-audit";
    }
}
