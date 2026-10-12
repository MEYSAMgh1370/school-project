package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «گزارش‌های سؤال پلتفرم»: یک shell نازک که فقط ناوبار مشترک را می‌سازد (مثل صفحه‌ی تعطیلی‌های سراسری).
 * داده client-side از {@code /api/platform/question-reports} می‌آید و اجازه‌دهی فقط روی لایه‌ی REST می‌ماند.
 * مسیر {@code /platform/**} از فیلتر tenant مستثناست.
 */
@Controller
public class PlatformQuestionReportsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public PlatformQuestionReportsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/platform/question-reports")
    public String platformQuestionReportsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "platform-question-reports";
    }
}
