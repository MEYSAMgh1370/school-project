package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import com.example.roshdeandishe.web.model.SchoolEntryModel;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.util.Optional;

/**
 * درِ ورودی برنددارِ هر مدرسه - /s/{slug} (Tier 1 از تصمیم «دامنه‌ی
 * اختصاصی مدرسه»، CLAUDE.md §11): مدرسه نشانی خودش را بین اعضایش پخش
 * می‌کند و عضو از همان در وارد می‌شود، نه از صفحه‌ی عمومی پلتفرم. صفحه
 * عمومی است (مثل / و /activate، بدون navbar - navbar احراز هویت لازم
 * دارد) و فقط نام عمومی مدرسه را رندر می‌کند.
 * <p>
 * مدرسه‌ی معلق (SUSPENDED) عمداً مثل ناموجود رفتار می‌شود - قاعده‌ی
 * presentation «مدرسه‌ی معلق را تبلیغ نکن»، اینجا نه در Facade.
 * <p>
 * برخلاف LandingPageController، کاربر احرازشده ری‌دایرکت *نمی‌شود*: باید
 * اول همین صفحه کلیک «ادامه به پیشخوان» را ببیند تا localStorage
 * (selectedSchoolId) با کلیک ست شود و پیشخوان با همین مدرسه بالا بیاید -
 * ری‌دایرکت سمت سرور دستش به localStorage نمی‌رسد.
 */
@Controller
public class SchoolEntryPageController {

    private final SchoolFacade schoolFacade;

    public SchoolEntryPageController(SchoolFacade schoolFacade) {
        this.schoolFacade = schoolFacade;
    }

    @GetMapping("/s/{slug}")
    public String schoolEntryPage(@PathVariable String slug, Principal principal, Model model,
                                  HttpServletResponse response) {
        Optional<SchoolInfo> school = schoolFacade.findSchoolBySlug(slug)
                .filter(info -> info.status() == SchoolStatus.ACTIVE);
        if (school.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        model.addAttribute("model", new SchoolEntryModel(
                school.map(info -> info.id().value()).orElse(null),
                school.map(SchoolInfo::name).orElse(null),
                principal != null
        ));
        return "school-entry";
    }
}
