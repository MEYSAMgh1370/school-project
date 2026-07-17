package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «آزمون‌های من» - سمت دانش‌آموز، همان مدل اعتماد و الگوی shell صفحه‌ی
 * /my-invoices: بدون X-School-ID (endpoint زیرمجموعه‌ی /api/me است)،
 * schoolId فقط از سوییچر navbar/localStorage خوانده می‌شود. شروع یک تلاش
 * از همین صفحه دانش‌آموز را به صفحه‌ی مستقل /exam-take (توکن‌محور، بدون
 * navbar/session - رجوع به ExamTakePageController) هدایت می‌کند.
 */
@Controller
public class MyExamsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public MyExamsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/my-exams")
    public String myExamsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "my-exams";
    }
}
