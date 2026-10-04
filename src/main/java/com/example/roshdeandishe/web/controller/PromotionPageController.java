package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی «ارتقای پایه» (openspec/changes/academic-year.md، فاز ۳): اجرای ارتقا، جا‌دادنِ دانش‌آموزانِ ارتقایافته
 * در کلاس‌های سالِ جدید و برگرداندنِ یک اجرا. داده از {@code academic.PromotionController} می‌آید؛ این کنترلر فقط
 * صفحه را می‌دهد (کنترلِ دسترسی سمتِ API است).
 */
@Controller
public class PromotionPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public PromotionPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/promotion")
    public String promotionPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "promotion";
    }
}
