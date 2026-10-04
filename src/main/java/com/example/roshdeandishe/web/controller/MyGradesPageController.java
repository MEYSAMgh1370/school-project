package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import com.example.roshdeandishe.web.model.WardsAssembler;
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
 * <p>
 * تنها داده‌ی سمت سرور، «نامِ فرزندانِ لینک‌شده» است (WardsAssembler.
 * wardRefsForCurrentUser): API نمره‌ها نام دانش‌آموز نمی‌دهد و عنوانِ هر
 * بخشِ آکاردئون باید نام باشد، نه شناسه.
 */
@Controller
public class MyGradesPageController {

    private final NavbarModelAssembler navbarModelAssembler;
    private final WardsAssembler wardsAssembler;

    public MyGradesPageController(NavbarModelAssembler navbarModelAssembler, WardsAssembler wardsAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
        this.wardsAssembler = wardsAssembler;
    }

    @GetMapping("/my-grades")
    public String myGradesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        model.addAttribute("wards", wardsAssembler.wardRefsForCurrentUser());
        return "my-grades";
    }
}
