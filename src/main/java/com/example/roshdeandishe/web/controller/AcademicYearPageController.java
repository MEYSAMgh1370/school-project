package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «سال تحصیلی» - انتخابِ سال تحصیلی جاریِ مدرسه توسط مدیر (فاز ۰،
 * openspec/changes/academic-year.md). یک shell نازک مثل /classes: فقط ناوبار
 * مشترک را می‌سازد؛ داده و ذخیره کاملاً client-side از school.AcademicYearController
 * می‌آید (GET /api/me/schools/{id}/academic-year و PUT /api/admin/schools/{id}/academic-year).
 * اجازه‌دهی (ACADEMIC_YEAR_MANAGE) فقط روی لایه‌ی REST می‌ماند.
 */
@Controller
public class AcademicYearPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public AcademicYearPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/academic-year")
    public String academicYearPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "academic-year";
    }
}
