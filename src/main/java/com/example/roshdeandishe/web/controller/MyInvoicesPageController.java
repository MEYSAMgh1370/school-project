package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «فاکتورهای من» - برای STUDENT (فاکتورهای خودش) و PARENT (فاکتورهای
 * فرزندانِ واقعاً لینک‌شده‌اش). یک shell نازک مثل /finance: فقط ناوبار
 * مشترک را می‌سازد - داده کاملاً client-side از
 * finance.MyInvoicesController (GET /api/me/schools/{schoolId}/invoices،
 * از قبل موجود از فاز پرداخت‌ها) خوانده می‌شود. برخلاف /finance، این
 * endpoint خودِ schoolId را در path می‌گیرد نه از هدر X-School-ID -
 * my-invoices.js آن را از همان selectedSchoolId سوییچر می‌خواند و مستقیم
 * در URL می‌گذارد (بدون هدر X-School-ID، چون خودِ endpoint هم مثل بقیه‌ی
 * /api/me/** از TenantResolverFilter مستثناست).
 */
@Controller
public class MyInvoicesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public MyInvoicesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/my-invoices")
    public String myInvoicesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "my-invoices";
    }
}
