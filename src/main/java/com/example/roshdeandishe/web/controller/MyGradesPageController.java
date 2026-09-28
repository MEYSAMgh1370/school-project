package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «نمره‌های من» - برای STUDENT (نمره‌های خودش) و PARENT (نمره‌های
 * فرزندانِ واقعاً لینک‌شده‌اش). یک shell نازک مثل /my-invoices: فقط ناوبار
 * مشترک را می‌سازد - داده کاملاً client-side از academic.MyGradesController
 * (GET /api/me/schools/{schoolId}/grades، الگوی «من» موجود، بدون هیچ
 * @RequiresPermission/@RequiresModule) خوانده می‌شود. مثل /my-invoices، این
 * endpoint خودِ schoolId را در path می‌گیرد نه از هدر X-School-ID.
 */
@Controller
public class MyGradesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public MyGradesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/my-grades")
    public String myGradesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "my-grades";
    }
}
