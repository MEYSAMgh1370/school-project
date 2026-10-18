package com.example.roshdeandishe.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * نشانی قدیمیِ صفحه‌ی «سال تحصیلی»: انتخاب سال جاری به «تنظیمات مدرسه» (/settings) رفته است؛ بوک‌مارک‌ها و
 * پیوندهای قدیمی همین‌جا به آن می‌روند. REST (/api/.../academic-year) دست‌نخورده است.
 */
@Controller
public class AcademicYearPageController {

    @GetMapping("/academic-year")
    public String academicYearPage() {
        return "redirect:/settings";
    }
}
