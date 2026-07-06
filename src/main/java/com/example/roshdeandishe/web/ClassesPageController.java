package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مدیریت کلاس‌های مدرسه (دومین برش UI مدیریتی، بعد از People -
 * UX_ROADMAP.md). یک shell نازک مثل /people: فقط ناوبار مشترک را می‌سازد -
 * داده‌ی کلاس‌ها و ساخت کلاس تازه کاملاً client-side و از طریق REST موجود
 * در academic.ClassController انجام می‌شود. نام معلم (در صورت تخصیص) از
 * طریق iam.MembershipController سمت کلاینت resolve می‌شود - academic هیچ
 * دیدی به نام‌های iam ندارد (فقط teacherId را به‌عنوان یک Long مات نگه
 * می‌دارد، طبق مرز ماژولی). اجازه‌دهی هم کاملاً روی لایه‌ی REST می‌ماند.
 */
@Controller
public class ClassesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public ClassesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/classes")
    public String classesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "classes";
    }
}
