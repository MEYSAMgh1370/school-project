package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی ثبت‌نام دانش‌آموز (برش چهارم UI مدیریتی، بعد از Classes -
 * UX_ROADMAP.md). یک shell نازک مثل /classes: فقط ناوبار مشترک را می‌سازد -
 * ثبت‌نام و فهرست دانش‌آموزان کاملاً client-side و از طریق REST موجود در
 * academic.StudentController انجام می‌شود. نام دانش‌آموز از طریق
 * iam.MembershipController سمت کلاینت resolve می‌شود (همان الگوی
 * teacherName در classes.js) - academic هیچ دیدی به نام‌های iam ندارد.
 */
@Controller
public class StudentsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public StudentsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/students")
    public String studentsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "students";
    }
}
