package com.example.roshdeandishe.iam.shared;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * فقط رندر صفحه‌ی لاگین - احراز هویت واقعی همچنان در
 * {@link AuthController#login} (POST /api/auth/login) انجام می‌شود؛
 * جاوااسکریپت صفحه همان endpoint را صدا می‌زند و در صورت موفقیت به مقصد
 * مجازِ پس از ورود می‌رود. همان الگوی {@link AccountActivationPageController}:
 * در iam.shared، چون لاگین دقیقاً دغدغه‌ی AuthController/SecurityConfig است.
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("postAuthenticationDestination", PostAuthenticationDestination.from(next));
        return "login";
    }
}
