package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مدیریت برنامه‌های مطالعه (دومین برش UI پورت moshavere - CLAUDE.md
 * §۳/§۴/§۷). shell نازک مثل /curriculum: فقط ناوبار مشترک - داده کاملاً
 * client-side و از طریق REST موجود در studyplan.controller.PlanController/
 * IntervalScheduleController/StudyIntervalController انجام می‌شود.
 * <p>
 * دامنه‌ی عمدی این برش: ساخت برنامه با وزن‌دهی درس + تخصیص به دانش‌آموز +
 * برنامه‌ی زمانی هفتگی پیش‌فرض دانش‌آموز + مدیریت بازه‌های مطالعه‌ی هفته
 * (شامل پیشنهاد خودکار). WeekCourseController (سرفصل‌های هفتگی یک برنامه)
 * و کپی‌کردن هفته بین چند دانش‌آموز عمداً در این برش ساخته نشدند - هیچ‌کدام
 * به چیز دیگری در این صفحه یا ماژول exam وابسته نیستند (getRecentTopics
 * exam از خودِ بازه‌های مطالعه‌شده می‌خواند، نه از WeekCourse)، پس اولویت
 * پایین‌تری نسبت به مسیر اصلیِ برنامه→تخصیص→زمان‌بندی→بازه دارند؛ اگر لازم
 * شد یک برش جداگانه‌اند.
 */
@Controller
public class StudyPlansPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public StudyPlansPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/study-plans")
    public String studyPlansPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "study-plans";
    }
}
