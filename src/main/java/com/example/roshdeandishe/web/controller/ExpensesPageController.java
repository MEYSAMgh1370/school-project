package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی هزینه‌های مدرسه - همان الگوی shell نازکِ FinancePageController:
 * دسته‌ها/طرف‌حساب‌ها/هزینه‌ها/پیوست‌ها/گزارش‌ها کاملاً client-side و از
 * طریق finance.ExpenseController انجام می‌شود؛ این کنترلر فقط ناوبار
 * مشترک را می‌سازد.
 */
@Controller
public class ExpensesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public ExpensesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/expenses")
    public String expensesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "expenses";
    }
}
