package com.example.roshdeandishe.iam.shared;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * داشبورد read-only بعد از لاگین: «من کی‌ام و کجا عضوم». server-rendered
 * از روی IamFacade (همین ماژول)، نه fetch کلاینتی - هم‌راستا با ترجیح
 * پروژه به type-safety زمان کامپایل JTE.
 * <p>
 * دو محدودیت عمدی این نسخه:
 * 1) نام مدرسه نمایش داده نمی‌شود، فقط schoolId - نام مدرسه مال ماژول
 *    school است و وابستگی iam→school حلقه ایجاد می‌کند (school از قبل به
 *    iam وابسته است). ترکیب iam+school برای UI به لایه/تصمیم دیگری نیاز
 *    دارد که هنوز گرفته نشده.
 * 2) هیچ سوییچ مدرسه/نقشی وجود ندارد - آن UX هنوز Open Question 2 در
 *    CLAUDE.md است و بدون بحث صریح نباید حل شود؛ این صفحه فقط فهرست را
 *    نمایش می‌دهد.
 */
@Controller
public class DashboardPageController {

    private final IamFacade iamFacade;

    public DashboardPageController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        UserId userId = iamFacade.currentUserId();
        UserProfile profile = iamFacade.findUser(userId).orElseThrow();

        List<MembershipRow> memberships = iamFacade.findMembershipsByUser(userId).stream()
                .map(m -> new MembershipRow(m.schoolId(), m.roleCode(), m.active()))
                .toList();

        model.addAttribute("model", new DashboardModel(profile.fullName(), profile.email(), memberships));
        return "dashboard";
    }

    public record DashboardModel(String fullName, String email, List<MembershipRow> memberships) {
    }

    public record MembershipRow(Long schoolId, String roleCode, boolean active) {
    }
}
