package com.example.roshdeandishe.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * جلسه‌ی زمان‌دارِ آزمون‌دهی - صفحه‌ی مستقل، بدون navbar/session، دقیقاً
 * مثل {@code AccountActivationPageController}: توکن جلسه‌ی آزمون خودش
 * اعتبار است (رجوع به کلاس‌داک exam.ExamFacade و exam.ExamTakeController)،
 * پس این صفحه هم مثل REST آن permitAll است (SecurityConfig) و از
 * TenantResolverFilter مستثنا (بدون X-School-ID). navbar عمداً رندر
 * نمی‌شود - NavbarModelAssembler به یک کاربر احرازهویت‌شده نیاز دارد که
 * اینجا لزوماً وجود ندارد. لینک شروع فقط از /my-exams («شروع آزمون») می‌آید
 * - مثل /schools/new یک صفحه‌ی یک‌باره‌ی تراکنشی است، نه بخشی از ناوبری
 * دائمی، پس در navbar لینک ندارد. برخلاف /activate، توکن/expireAt را
 * Controller به مدل تزریق نمی‌کند - چون static/js/exam-take.js (قاعده‌ی
 * جانمایی کد، نه Alpine اینلاین مثل activate.jte) یک فایل ساده‌ی JS است و
 * قالب JTE پردازش نمی‌شود، پس مستقیماً از query string خودِ صفحه
 * (window.location.search) می‌خواند - تزریق سمت سرور به یک کانال دوم بدون
 * فایده بود.
 */
@Controller
public class ExamTakePageController {

    @GetMapping("/exam-take")
    public String examTakePage() {
        return "exam-take";
    }
}
