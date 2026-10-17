package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی «بات بله‌ی مدرسه» - همان الگوی shell نازکِ FinanceAuditPageController: فقط ناوبار مشترک را می‌سازد؛
 * ثبت، تعویض و حذفِ بات client-side و از طریق {@code /api/admin/schools/{schoolId}/bale-bot}
 * (مجوز BALE_BOT_MANAGE، بدون ماژول) انجام می‌شود.
 */
@Controller
public class SchoolBotPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public SchoolBotPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/bale-bot")
    public String baleBotPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "bale-bot";
    }
}
