package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «تنظیمات مدرسه» (school-settings): سال تحصیلی، اقساط، گیرندگان یادآوری و بات بله در یک صفحه. یک shell نازک مثل
 * /academic-year: فقط ناوبار مشترک را می‌سازد؛ GET /api/admin/schools/{id}/settings هر بخش را فقط برای دارنده‌ی
 * مجوزش پر می‌کند و نوشتن در endpointهای خودِ هر تنظیم است. اجازه‌دهی فقط روی لایه‌ی REST می‌ماند.
 */
@Controller
public class SettingsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public SettingsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "settings";
    }
}
