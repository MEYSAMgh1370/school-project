package com.example.roshdeandishe.iam.shared;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * فقط رندر صفحه‌ی ثبت‌نام عمومی - ثبت‌نام واقعی در
 * {@link AuthController#register} (POST /api/auth/register) انجام می‌شود؛
 * جاوااسکریپت صفحه همان endpoint را صدا می‌زند و در صورت موفقیت، با همان
 * اعتبارنامه‌ها لاگین کرده و به مقصد مجازِ پس از ورود می‌رود. همان الگوی
 * {@link LoginPageController}: در iam.shared، چون ثبت‌نام دقیقاً دغدغه‌ی
 * AuthController/SecurityConfig است.
 */
@Controller
public class RegisterPageController {

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("postAuthenticationDestination", PostAuthenticationDestination.from(next));
        return "register";
    }
}
