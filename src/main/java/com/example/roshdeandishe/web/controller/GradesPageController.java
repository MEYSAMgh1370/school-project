package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * دفترچه‌نمره‌ی ادمین/معلم (اولین برش UI برای مدل نمره - CLAUDE.md §11 Q8،
 * پیش از این فقط backend+REST داشت). یک shell نازک مثل /finance: فقط
 * ناوبار مشترک را می‌سازد - انتخاب کلاس/سال/نوبت، ثبت و مرور نمرات کاملاً
 * client-side و از طریق REST موجود در academic.GradeController انجام
 * می‌شود (GRADES_EDIT روی هر دو متد - رجوع کنید به grades.js برای تفکیک
 * دو نوع 403).
 */
@Controller
public class GradesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public GradesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/grades")
    public String gradesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "grades";
    }
}
