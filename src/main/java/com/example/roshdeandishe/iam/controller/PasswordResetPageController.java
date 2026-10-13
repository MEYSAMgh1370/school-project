package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * فقط رندر صفحه‌های بازیابی رمز (password-reset-by-email.md)؛ کارِ واقعی در
 * {@link AuthController#requestPasswordReset} و {@link AuthController#confirmPasswordReset}. هر دو صفحه
 * عمومی‌اند (مثل /activate). {@code available} فقط تصمیمِ نمایشِ فرم است: وقتی ایمیل پیکربندی نشده، صفحه
 * به‌جای فرمی که لینکش نمی‌رسد توضیح می‌دهد.
 */
@Controller
public class PasswordResetPageController {

    private final IamFacade iamFacade;

    public PasswordResetPageController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("available", iamFacade.isPasswordResetAvailable());
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        // توکن ورودیِ نامطمئن است: فقط به‌صورت data-attributeِ escape‌شده به صفحه می‌رود (مثل /activate).
        model.addAttribute("token", token == null ? "" : token);
        return "reset-password";
    }
}
