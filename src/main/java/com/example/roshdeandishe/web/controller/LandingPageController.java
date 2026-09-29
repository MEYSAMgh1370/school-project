package com.example.roshdeandishe.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * صفحه‌ی اول عمومی (تصمیم محصولی ۲۰۲۶-۰۷-۰۶): درِ ورودی محصول برای
 * بازدیدکننده‌ی ناشناس - ورود، ثبت‌نام، و «ثبت مدرسه» (مسیر اختصاصی
 * /schools/new؛ فرم ساخت مدرسه دیگر روی داشبورد نیست، چون پیشنهادِ
 * «مدرسه بسازید» به دانش‌آموز/والدِ دعوت‌شده بی‌معنا بود). کاربر
 * احراز هویت‌شده مستقیم به داشبورد می‌رود - این صفحه فقط برای
 * ناشناس‌هاست.
 */
@Controller
public class LandingPageController {

    @GetMapping("/")
    public String landingPage(Principal principal) {
        return principal != null ? "redirect:/dashboard" : "landing";
    }
}
