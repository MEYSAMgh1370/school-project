package com.example.roshdeandishe.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی مدیریت آزمون‌ها (سومین و آخرین برش UI پورت moshavere - CLAUDE.md
 * §۳/§۴/§۷). shell نازک مثل /curriculum و /study-plans: فقط ناوبار مشترک -
 * داده کاملاً client-side و از طریق REST موجود در exam.ExamController/
 * ExamResultController انجام می‌شود.
 * <p>
 * فقط سمت مدیریتی (تألیف/تخصیص/نتایج، EXAM_MANAGE/EXAM_VIEW) اینجاست.
 * سمت دانش‌آموز (فهرست آزمون‌های من + آزمون‌دهی توکن‌محور + نتیجه‌ی خودم)
 * صفحه‌ی جدای خودش را دارد - رجوع به MyExamsPageController/
 * ExamTakePageController - چون مدل اعتماد و مکانیزم مجوزش کاملاً متفاوت
 * است (بدون X-School-ID/session برای بخش آزمون‌دهی، رجوع به کلاس‌داک
 * ExamFacade).
 */
@Controller
public class ExamsPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public ExamsPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/exams")
    public String examsPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "exams";
    }
}
