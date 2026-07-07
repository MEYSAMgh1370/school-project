package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مالی مدرسه (برش ششم و آخرین برش SCHOOL_ADMIN، بعد از Enrollment -
 * UX_ROADMAP.md). یک shell نازک مثل /students: فقط ناوبار مشترک را می‌سازد -
 * تنظیم شهریه و فهرست فاکتورها کاملاً client-side و از طریق REST موجود در
 * finance.FinanceController انجام می‌شود. این اولین صفحه‌ای است که باید
 * دو نوع 403 لایه‌ی کنترل دسترسی §5 (ModuleNotEntitled در برابر
 * PermissionDenied) را با پیام‌های متفاوت نمایش دهد - رجوع کنید به
 * finance.js.
 */
@Controller
public class FinancePageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public FinancePageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/finance")
    public String financePage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "finance";
    }
}
