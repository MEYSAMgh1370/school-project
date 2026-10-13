package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.config.PostAuthenticationDestination;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * فقط رندر صفحه‌ی لاگین - احراز هویت واقعی همچنان در
 * {@link AuthController#login} (POST /api/auth/login) انجام می‌شود؛
 * جاوااسکریپت صفحه همان endpoint را صدا می‌زند و در صورت موفقیت به مقصد
 * مجازِ پس از ورود می‌رود. همان الگوی {@link AccountActivationPageController}:
 * در iam.shared، چون لاگین دقیقاً دغدغه‌ی AuthController/SecurityConfig است.
 * <p>
 * کاربرِ از قبل واردشده فرم را نمی‌بیند و مستقیم به همان مقصدِ مجاز می‌رود
 * (مثل {@code LandingPageController}). {@code otpAvailable} فقط تصمیمِ نمایشِ
 * تبِ «کد پیامکی» است؛ خودِ {@code requestOtp} مستقل از آن fail-closed می‌ماند.
 * {@code registered} فقط برای پیامِ «حساب ساخته شد» بعد از ثبت‌نامی است که
 * ورودِ خودکارش شکست خورده (register.js).
 */
@Controller
public class LoginPageController {

    private final IamFacade iamFacade;

    public LoginPageController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next,
                            @RequestParam(required = false) String registered,
                            Principal principal, Model model) {
        String destination = PostAuthenticationDestination.from(next);
        if (principal != null) {
            return "redirect:" + destination;
        }
        model.addAttribute("postAuthenticationDestination", destination);
        model.addAttribute("otpAvailable", iamFacade.isOtpDeliveryAvailable());
        model.addAttribute("justRegistered", "1".equals(registered));
        return "login";
    }
}
