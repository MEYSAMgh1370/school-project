package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.web.dto.DashboardModel;
import com.example.roshdeandishe.web.dto.MembershipRow;
import com.example.roshdeandishe.web.dto.SchoolEntryRow;
import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import com.example.roshdeandishe.web.model.WardsAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /**
     * نقشِ مدیرِ مدرسه به‌صورت literal چک می‌شود (مثل همان چکِ literal که
     * finance.FinanceController روی SCHOOL_ADMIN/FINANCE_ADMIN دارد و چکِ
     * literal PARENT در academic) - نقش‌های سفارشی عمداً روی این چک اثری
     * ندارند (CLAUDE.md §4، «Custom roles»). اینجا مرزِ امنیتی هم نیست:
     * نشانیِ /s/{slug} خودش عمومی است، این فقط تصمیمِ «به چه کسی نشانش
     * بدهیم» است.
     */
    private static final String SCHOOL_ADMIN_ROLE = "SCHOOL_ADMIN";

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final NavbarModelAssembler navbarModelAssembler;
    private final WardsAssembler wardsAssembler;

    public DashboardPageController(IamFacade iamFacade, SchoolFacade schoolFacade,
                                   NavbarModelAssembler navbarModelAssembler, WardsAssembler wardsAssembler) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.navbarModelAssembler = navbarModelAssembler;
        this.wardsAssembler = wardsAssembler;
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        UserId userId = iamFacade.currentUserId();
        UserProfile profile = iamFacade.findUser(userId).orElseThrow();
        List<MembershipInfo> memberships = iamFacade.findMembershipsByUser(userId);

        // مدرسه‌ها را یک‌بار per-school می‌خوانیم و هم نام و هم slug را از
        // همین یک خواندن برمی‌داریم (قبلاً فقط نام از assembler گرفته می‌شد).
        Map<Long, Optional<SchoolInfo>> schools = new LinkedHashMap<>();
        for (MembershipInfo membership : memberships) {
            schools.computeIfAbsent(membership.schoolId(), id -> schoolFacade.findSchool(new SchoolId(id)));
        }

        List<MembershipRow> rows = memberships.stream()
                .map(m -> new MembershipRow(m.schoolId(), schoolName(schools.get(m.schoolId()), m.schoolId()),
                        m.roleCode(), m.active()))
                .toList();

        model.addAttribute("model", new DashboardModel(profile.fullName(), profile.email(), profile.phoneNumber(),
                rows, schoolEntries(memberships, schools), WardsAssembler.isParent(memberships),
                wardsAssembler.forParent(userId, memberships)));
        model.addAttribute("navbar", navbarModelAssembler.assemble(profile, memberships));
        return "dashboard";
    }

    /**
     * نشانیِ ورودِ اختصاصیِ مدرسه‌هایی که کاربر در آن‌ها مدیرِ فعال است -
     * تا مدیر بتواند نشانی را بین اعضای مدرسه پخش کند (UX_ROADMAP §6.4:
     * slug تا امروز فقط نوشتنی بود و هیچ‌جا نمایش داده نمی‌شد). مدرسه‌ی
     * بدون slug هم فهرست می‌شود - با توضیح این‌که نشانی فقط هنگام ثبت
     * مدرسه انتخاب می‌شود؛ ویرایشِ بعدیِ آن عمداً ساخته نشده (CLAUDE.md
     * §11 Q9). کاربرِ بدون نقشِ مدیر فهرستِ خالی می‌گیرد و کلِ باکس در
     * template رندر نمی‌شود.
     */
    private List<SchoolEntryRow> schoolEntries(List<MembershipInfo> memberships,
                                               Map<Long, Optional<SchoolInfo>> schools) {
        List<SchoolEntryRow> entries = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (MembershipInfo membership : memberships) {
            if (!membership.active() || !SCHOOL_ADMIN_ROLE.equals(membership.roleCode())) {
                continue;
            }
            // یک کاربر می‌تواند چند عضویت در یک مدرسه داشته باشد - هر مدرسه
            // فقط یک‌بار در این فهرست می‌آید.
            if (!seen.add(membership.schoolId())) {
                continue;
            }
            Optional<SchoolInfo> school = schools.getOrDefault(membership.schoolId(), Optional.empty());
            entries.add(new SchoolEntryRow(schoolName(school, membership.schoolId()),
                    school.map(SchoolInfo::slug).orElse(null)));
        }
        return entries;
    }

    /**
     * اگر مدرسه پیدا نشد (نباید رخ دهد، ولی iam فقط یک Long مات نگه می‌دارد)
     * به‌جای شکستن صفحه شناسه را نشان می‌دهیم - همان fallback ای که
     * NavbarModelAssembler.schoolName دارد.
     */
    private String schoolName(Optional<SchoolInfo> school, Long schoolId) {
        return school.map(SchoolInfo::name).orElse("مدرسه #" + schoolId);
    }
}
