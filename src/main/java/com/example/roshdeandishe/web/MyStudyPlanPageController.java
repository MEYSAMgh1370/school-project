package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «برنامه‌ی مطالعه‌ی من» - سمت دانش‌آموز، همان مدل اعتماد و الگوی shell
 * صفحه‌های /my-exams و /my-invoices: بدون X-School-ID (endpoint زیرمجموعه‌ی
 * /api/me است)، schoolId فقط از سوییچر navbar/localStorage خوانده می‌شود.
 * فقط ناوبار مشترک را می‌سازد - داده کاملاً client-side از
 * studyplan.MyStudyIntervalController خوانده می‌شود.
 */
@Controller
public class MyStudyPlanPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public MyStudyPlanPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/my-study-plan")
    public String myStudyPlanPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "my-study-plan";
    }
}
