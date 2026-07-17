package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مدیریت تاکسونومی سرفصل‌ها + بانک سؤال (اولین برش UI برای پورت
 * moshavere - CLAUDE.md بخش‌های ۳/۴/۷). یک shell نازک مثل /classes: فقط
 * ناوبار مشترک را می‌سازد - داده‌ی سرفصل‌ها/سؤال‌ها کاملاً client-side و از
 * طریق REST موجود در curriculum.TopicController/QuestionController انجام
 * می‌شود. CURRICULUM_MANAGE/CURRICULUM_VIEW هر دو به SCHOOL_ADMIN و TEACHER
 * یکسان داده شده‌اند (بر خلاف Classes) پس این صفحه، برخلاف /classes، تفاوت
 * دسترسی خواندن/نوشتن بین این دو نقش ندارد.
 */
@Controller
public class CurriculumPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public CurriculumPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/curriculum")
    public String curriculumPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "curriculum";
    }
}
