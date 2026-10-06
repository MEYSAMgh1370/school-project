package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «تعطیلی‌های سراسری» (openspec/changes/yearly-plan.md فاز C): یک shell نازک که فقط ناوبار مشترک را می‌سازد.
 * صفحه برای مدیر پلتفرم است که معمولاً هیچ عضویت مدرسه‌ای ندارد؛ داده client-side از
 * {@code /api/platform/**} می‌آید و اجازه‌دهی (PLATFORM_HOLIDAY_MANAGE) فقط روی لایه‌ی REST می‌ماند.
 * مسیر {@code /platform/**} از فیلتر tenant مستثناست.
 */
@Controller
public class PlatformHolidaysPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public PlatformHolidaysPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/platform/holidays")
    public String platformHolidaysPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "platform-holidays";
    }
}
