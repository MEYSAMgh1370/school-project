package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «برنامه‌ی سالانه» (openspec/changes/yearly-plan.md): یک shell نازک مثل /calendar که فقط ناوبار مشترک را
 * می‌سازد؛ داده client-side از {@code school.YearlyPlanController} می‌آید (خواندن: هر عضو؛ افزودن/حذف:
 * YEARLY_PLAN_MANAGE). اجازه‌دهی فقط روی لایه‌ی REST می‌ماند.
 */
@Controller
public class YearlyPlanPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public YearlyPlanPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/yearly-plan")
    public String yearlyPlanPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "yearly-plan";
    }
}
