package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «برنامه‌ی کلاسی» (openspec/changes/class-timetable.md): دو shell نازک مثل /classes و
 * /my-grades که فقط ناوبار مشترک را می‌سازند؛ همه‌ی داده client-side از
 * academic.TimetableController (مدیر، ACADEMIC + TIMETABLE_MANAGE) و
 * academic.MyTimetableController (/api/me، بدون مجوز) می‌آید. اجازه‌دهی فقط روی
 * لایه‌ی REST می‌ماند.
 */
@Controller
public class TimetablePageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public TimetablePageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/timetable")
    public String timetablePage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "timetable";
    }

    @GetMapping("/my-timetable")
    public String myTimetablePage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "my-timetable";
    }
}
