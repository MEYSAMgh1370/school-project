package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی اختصاصی ثبت مدرسه (تصمیم محصولی ۲۰۲۶-۰۷-۰۶): جایگزین فرمِ
 * قبلی روی داشبورد. مسیر ورود از صفحه‌ی اول (/) است؛ ساخت مدرسه
 * self-service می‌ماند (CLAUDE.md §1 - سازنده بلافاصله SCHOOL_ADMIN
 * می‌شود) و دروازه‌ی واقعی همچنان entitlementهاست. جهت‌گیری آینده
 * (سازمان + تأیید پلتفرم) در CLAUDE.md §11 سؤال ۶ ثبت شده و به
 * زیرساخت PLATFORM_ADMIN (بند ۷ مورد ۱) گره خورده - فعلاً ساخته
 * نمی‌شود.
 */
@Controller
public class SchoolRegistrationPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public SchoolRegistrationPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/schools/new")
    public String schoolRegistrationPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "school-new";
    }
}
