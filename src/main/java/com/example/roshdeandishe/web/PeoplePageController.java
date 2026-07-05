package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مدیریت اعضای مدرسه (اولین برش UI مدیریتیِ بند ۷.۶ در
 * CLAUDE.md). یک shell نازک مثل /dashboard: فقط ناوبار مشترک را می‌سازد -
 * داده‌ی اعضا و همه‌ی جهش‌ها (افزودن عضو، فعال/غیرفعال، صدور توکن دعوت)
 * کاملاً client-side و از طریق REST موجود در iam.MembershipController
 * انجام می‌شود (چون مدرسه‌ی انتخاب‌شده فقط در localStorage است - سرور
 * نمی‌داند کدام مدرسه انتخاب شده، مگر از هدر X-School-ID که فقط جاوااسکریپت
 * صفحه می‌فرستد). اجازه‌دهی هم کاملاً روی لایه‌ی REST می‌ماند.
 */
@Controller
public class PeoplePageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public PeoplePageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/people")
    public String peoplePage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "people";
    }
}
