package com.example.roshdeandishe.iam.shared;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * فقط رندر صفحه‌ی لاگین - احراز هویت واقعی همچنان در
 * {@link AuthController#login} (POST /api/auth/login) انجام می‌شود؛
 * جاوااسکریپت صفحه همان endpoint را صدا می‌زند و در صورت موفقیت به
 * /dashboard می‌رود. همان الگوی {@link AccountActivationPageController}:
 * در iam.shared، چون لاگین دقیقاً دغدغه‌ی AuthController/SecurityConfig است.
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
