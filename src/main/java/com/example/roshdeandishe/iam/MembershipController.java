package com.example.roshdeandishe.iam;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * مدیریت عضویت‌های یک مدرسه‌ی مشخص - همان قابلیتی که "ادمین یک معلم جدید
 * را با نقش TEACHER اضافه می‌کند" یا "دسترسی یک دانش‌آموز خاص را غیرفعال
 * می‌کند" را پیاده می‌کند.
 * <p>
 * ⚠️ نکته‌ی مهم: {@code @RequiresPermission} بر اساس {@link TenantContext}
 * (که از هدر X-School-ID پر می‌شود) بررسی می‌کند، نه بر اساس {schoolId}
 * موجود در path. اگر این دو با هم مطابقت نداشته باشند (مثلاً کلاینت هدر
 * را برای مدرسه‌ی ۵ فرستاده اما در path مدرسه‌ی ۷ را صدا زده)، permission
 * check ممکن است با schoolId اشتباهی انجام شود در حالی که عملیات واقعی
 * روی schoolId دیگری اجرا می‌شود - یک مسیر امنیتی خطرناک. به همین دلیل هر
 * متد این کنترلر صریحاً این دو را با هم مقایسه می‌کند و در صورت
 * ناهم‌خوانی، درخواست را رد می‌کند.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/memberships")
public class MembershipController {

    private final IamFacade iamFacade;

    public MembershipController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @PostMapping
    @RequiresPermission("MEMBERSHIP_MANAGE")
    public MembershipResponse createMembership(@PathVariable Long schoolId, @RequestBody CreateMembershipRequest request) {
        TenantContext.requireMatches(schoolId);

        UserId userId = iamFacade.findOrCreateUser(request.email(), request.fullName());
        Long membershipId = iamFacade.createMembership(userId, schoolId, request.roleCode());

        MembershipInfo info = iamFacade.findMembership(userId, schoolId).orElseThrow();
        return MembershipResponse.from(membershipId, info);
    }

    @GetMapping
    @RequiresPermission("MEMBERSHIP_MANAGE")
    public List<MembershipResponse> listMemberships(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return iamFacade.findMembershipsBySchool(schoolId).stream()
                .map(info -> MembershipResponse.from(info.membershipId(), info))
                .toList();
    }

    @PatchMapping("/{membershipId}/active")
    @RequiresPermission("MEMBERSHIP_MANAGE")
    public void setMembershipActive(
            @PathVariable Long schoolId,
            @PathVariable Long membershipId,
            @RequestBody SetActiveRequest request
    ) {
        TenantContext.requireMatches(schoolId);
        iamFacade.setMembershipActive(membershipId, request.active());
    }

    /**
     * نسخه‌ی محدودترِ {@link #listMemberships} (UX_ROADMAP.md §6.4) - فقط
     * userId/fullName را برای مجموعه‌ای مشخص از کاربران برمی‌گرداند (نه
     * ایمیل، نقش، وضعیت فعال‌سازی هر عضو که MEMBERSHIP_MANAGE افشا می‌کند).
     * برای نقش‌هایی ساخته شده که باید نام چند کاربرِ *از قبل شناخته‌شده* (مثلاً
     * دانش‌آموزهای یک فاکتور یا یک کلاس) را resolve کنند، بدون این‌که کل
     * فهرست عضویت‌های مدرسه در اختیارشان باشد - همان دلیلی که TEACHER/
     * FINANCE_ADMIN عمداً MEMBERSHIP_MANAGE ندارند (CLAUDE.md §4). فقط
     * عضویت‌های فعال را برمی‌گرداند - همان فیلتری که کلاینت‌ها (people.js و
     * غیره) روی فهرست کامل هم اعمال می‌کنند.
     */
    @GetMapping("/names")
    @RequiresPermission("MEMBER_NAMES_VIEW")
    public List<MemberNameResponse> listMemberNames(@PathVariable Long schoolId, @RequestParam List<Long> userIds) {
        TenantContext.requireMatches(schoolId);

        Set<Long> requested = Set.copyOf(userIds);
        return iamFacade.findMembershipsBySchool(schoolId).stream()
                .filter(info -> info.active() && requested.contains(info.userId().value()))
                .map(info -> new MemberNameResponse(info.userId().value(), info.fullName()))
                .distinct()
                .toList();
    }

    @PostMapping("/{membershipId}/invite-token")
    @RequiresPermission("MEMBERSHIP_MANAGE")
    public InviteTokenResponse issueInviteToken(@PathVariable Long schoolId, @PathVariable Long membershipId) {
        TenantContext.requireMatches(schoolId);

        return iamFacade.issueInviteToken(membershipId)
                .map(issued -> new InviteTokenResponse(issued.token(), true, issued.emailSent()))
                .orElseGet(() -> new InviteTokenResponse(null, false, false));
    }


    public record CreateMembershipRequest(String email, String fullName, String roleCode) {
    }

    public record SetActiveRequest(boolean active) {
    }

    public record MemberNameResponse(Long userId, String fullName) {
    }

    public record MembershipResponse(
            Long membershipId, Long userId, String email, String fullName,
            Long schoolId, String roleCode, boolean active, boolean hasPassword
    ) {
        static MembershipResponse from(Long membershipId, MembershipInfo info) {
            return new MembershipResponse(
                    membershipId, info.userId().value(), info.email(), info.fullName(),
                    info.schoolId(), info.roleCode(), info.active(), info.hasPassword()
            );
        }
    }

    /**
     * emailSent=false با issued=true یعنی توکن صادر شد اما ایمیل به مقصد
     * نرسید (مثلاً SMTP در دسترس نبود) - ادمین باید لینک
     * /activate?token=... را دستی به فرد دعوت‌شده برساند.
     */
    public record InviteTokenResponse(String inviteToken, boolean issued, boolean emailSent) {
    }
}
