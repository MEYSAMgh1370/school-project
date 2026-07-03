package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * پاسخ به سوال دقیق‌تر از ماژول‌محور: "آیا کاربر X، در مدرسه Y، با نقش
 * (یا نقش‌های) فعالش، اجازه‌ی انجام عمل خاص Z را دارد؟"
 * <p>
 * این لایه بعد از {@link AccessDecisionService} (که فقط "آیا مدرسه ماژول
 * FINANCE را خریده؟" را بررسی می‌کند) اجرا می‌شود. حتی اگر مدرسه FINANCE
 * را فعال کرده باشد، یک دانش‌آموز عادی هنوز نباید بتواند فاکتور صادر کند -
 * این دقیقاً همان چیزی است که این سرویس بررسی می‌کند.
 * <p>
 * نکته‌ی کلیدی برای "غیرفعال کردن دسترسی یک دانش‌آموز خاص": این متد فقط
 * Membershipهایی با {@code active = true} را در نظر می‌گیرد. اگر مدرسه
 * یک Membership را غیرفعال کند، حتی اگر نقش آن همچنان Permission مورد نظر
 * را داشته باشد، دیگر اعمال نمی‌شود.
 */
@Service
class PermissionDecisionService {

    private final SchoolMembershipRepository schoolMembershipRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    PermissionDecisionService(
            SchoolMembershipRepository schoolMembershipRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    AccessDecision decide(Long userId, String permissionCode, AccessContext context) {
        // فعلاً فقط context مدرسه‌ای پشتیبانی می‌شود؛ نقش‌های سطح پلتفرم
        // (مثل PLATFORM_ADMIN در PlatformContext) در فاز بعدی اضافه می‌شوند.
        if (!(context instanceof AccessContext.SchoolContext schoolContext)) {
            return new AccessDecision.Denied(
                    "بررسی Permission فعلاً فقط برای context مدرسه‌ای پشتیبانی می‌شود."
            );
        }

        PermissionEntity permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IamException(new IamError.PermissionNotFound(permissionCode)));

        List<SchoolMembershipEntity> activeMemberships = schoolMembershipRepository
                .findByUserIdAndSchoolIdAndActiveTrue(userId, schoolContext.schoolId());

        if (activeMemberships.isEmpty()) {
            boolean hasAnyMembershipAtAll = !schoolMembershipRepository
                    .findByUserIdAndSchoolId(userId, schoolContext.schoolId())
                    .isEmpty();

            return hasAnyMembershipAtAll
                    ? new AccessDecision.Denied("عضویت کاربر در این مدرسه غیرفعال شده است.")
                    : new AccessDecision.Denied("کاربر هیچ عضویتی در این مدرسه ندارد.");
        }

        boolean granted = activeMemberships.stream()
                .anyMatch(membership -> rolePermissionRepository.existsByRoleIdAndPermissionId(
                        membership.getRoleId(), permission.getId()
                ));

        return granted
                ? new AccessDecision.Granted("یکی از نقش‌های فعال کاربر این Permission را دارد.")
                : new AccessDecision.Denied(
                        "هیچ‌کدام از نقش‌های فعال کاربر در این مدرسه Permission '" + permissionCode + "' را ندارند."
                  );
    }
}
