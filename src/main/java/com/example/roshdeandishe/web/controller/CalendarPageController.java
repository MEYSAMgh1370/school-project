package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «تقویم مدرسه» (openspec/changes/attendance.md، فاز ۰): یک shell نازک مثل /timetable که فقط ناوبار
 * مشترک را می‌سازد؛ همه‌ی داده client-side از {@code school.CalendarController}
 * ({@code /api/admin/schools/{id}/calendar}، مجوز CALENDAR_MANAGE) می‌آید. اجازه‌دهی فقط روی لایه‌ی REST
 * می‌ماند.
 */
@Controller
public class CalendarPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public CalendarPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/calendar")
    public String calendarPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "calendar";
    }
}
