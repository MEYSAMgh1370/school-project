package com.example.roshdeandishe.web;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * داشبورد بعد از لاگین: «من کی‌ام و کجا عضوم» + سوییچر مدرسه/نقش
 * (Open Question 2 - حل‌شده: دراپ‌داون روی نام مدرسه برای انتخاب مدرسه،
 * دراپ‌داون روی نام شخص برای نقش در همان مدرسه).
 * <p>
 * در ماژول web قرار دارد، نه iam.shared: این صفحه داده‌ی دو ماژول را کنار
 * هم می‌گذارد (عضویت‌ها از iam + نام مدرسه از school) و وابستگی iam→school
 * حلقه ایجاد می‌کرد. ماژول web فقط لایه‌ی ترکیب UI است - Facade، entity و
 * REST خودش را ندارد و هیچ ماژولی به آن وابسته نمی‌شود.
 * <p>
 * انتخاب سوییچر صرفاً client-side است (localStorage) - جزئیات و محدودیت
 * مهمش (نقش فقط نمایشی است، backend همه‌ی نقش‌های فعال را چک می‌کند) در
 * CLAUDE.md بند ۱۱ سؤال ۲ ثبت شده. کاربر بدون هیچ عضویتی (فرد B2C آینده)
 * هم باید بدون خطا همین صفحه را ببیند - دراپ‌داون مدرسه در آن حالت اصلاً
 * رندر نمی‌شود.
 */
@Controller
public class DashboardPageController {

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;

    public DashboardPageController(IamFacade iamFacade, SchoolFacade schoolFacade) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        UserId userId = iamFacade.currentUserId();
        UserProfile profile = iamFacade.findUser(userId).orElseThrow();
        List<MembershipInfo> memberships = iamFacade.findMembershipsByUser(userId);

        // نام مدرسه‌ها را یک‌بار per-school حل می‌کنیم؛ اگر مدرسه‌ای پیدا
        // نشد (نباید رخ دهد، ولی iam فقط یک Long مات نگه می‌دارد) به جای
        // شکستن صفحه، شناسه را نمایش می‌دهیم.
        Map<Long, String> schoolNames = new LinkedHashMap<>();
        for (MembershipInfo membership : memberships) {
            schoolNames.computeIfAbsent(membership.schoolId(), id ->
                    schoolFacade.findSchool(new SchoolId(id))
                            .map(SchoolFacade.SchoolInfo::name)
                            .orElse("مدرسه #" + id));
        }

        List<MembershipRow> rows = memberships.stream()
                .map(m -> new MembershipRow(m.schoolId(), schoolNames.get(m.schoolId()), m.roleCode(), m.active()))
                .toList();

        // گزینه‌های سوییچر: فقط عضویت‌های فعال، گروه‌شده per-school با
        // فهرست نقش‌های فعالِ همان مدرسه (برای دراپ‌داون نقش).
        Map<Long, SchoolOption> options = new LinkedHashMap<>();
        for (MembershipInfo membership : memberships) {
            if (!membership.active()) {
                continue;
            }
            options.computeIfAbsent(membership.schoolId(), id ->
                            new SchoolOption(id, schoolNames.get(id), new java.util.ArrayList<>()))
                    .roles().add(membership.roleCode());
        }

        model.addAttribute("model", new DashboardModel(
                profile.fullName(), profile.email(), rows, List.copyOf(options.values())));
        return "dashboard";
    }

    public record DashboardModel(String fullName, String email,
                                 List<MembershipRow> memberships, List<SchoolOption> schools) {
    }

    public record MembershipRow(Long schoolId, String schoolName, String roleCode, boolean active) {
    }

    public record SchoolOption(Long id, String name, List<String> roles) {
    }
}
