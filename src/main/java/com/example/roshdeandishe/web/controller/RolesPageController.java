package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * صفحه‌ی نقش‌ها و دسترسی‌ها - حل‌کننده‌ی Open Question 3 در CLAUDE.md.
 * یک shell نازک مثل بقیه‌ی صفحه‌های مدیریتی: فهرست نقش‌های سیستمی + نقش‌های
 * سفارشیِ مدرسه به‌همراه Permissionهایشان، و فرم ساخت نقش سفارشیِ تازه -
 * کاملاً client-side از iam.RoleController (ROLE_MANAGE، فقط SCHOOL_ADMIN).
 */
@Controller
public class RolesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public RolesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/roles")
    public String rolesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "roles";
    }
}
