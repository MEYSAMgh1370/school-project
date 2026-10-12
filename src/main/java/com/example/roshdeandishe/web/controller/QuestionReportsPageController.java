package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی صف «گزارش‌های سؤال» مدرسه. shell نازک مثل /curriculum: فقط ناوبار
 * مشترک را می‌سازد؛ فهرست و تغییر وضعیت گزارش‌ها کاملاً client-side و از طریق
 * REST موجود در curriculum.QuestionReportController انجام می‌شود (خواندن با
 * CURRICULUM_VIEW، تغییر وضعیت با CURRICULUM_MANAGE روی سؤال‌های خودِ مدرسه).
 */
@Controller
public class QuestionReportsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public QuestionReportsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/question-reports")
    public String questionReportsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "question-reports";
    }
}
