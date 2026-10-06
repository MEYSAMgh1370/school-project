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
    private final PlatformAdminRepository platformAdminRepository;

    PermissionDecisionService(
            SchoolMembershipRepository schoolMembershipRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            PlatformAdminRepository platformAdminRepository
    ) {
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    /** آیا این کاربر در {@code iam.platform_admins} است؟ (نمایش ناوبار؛ مجوزی نمی‌دهد.) */
    boolean isPlatformAdmin(Long userId) {
        return userId != null && platformAdminRepository.existsById(userId);
    }

    AccessDecision decide(Long userId, String permissionCode, AccessContext context) {
        if (context instanceof AccessContext.PlatformContext) {
            return decidePlatform(userId, permissionCode);
        }
        if (!(context instanceof AccessContext.SchoolContext schoolContext)) {
            return new AccessDecision.Denied("نوع context پشتیبانی نمی‌شود.");
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

    /**
     * فقط مجموعه‌ی ثابتِ {@link PlatformPermissions#CODES} و فقط برای کاربرِ
     * {@code platform_admins}. هیچ نقش/عضویتی خوانده نمی‌شود، پس مجوز مدرسه‌ای هرگز از اینجا
     * اعطا نمی‌شود؛ و برعکس، شاخه‌ی SchoolContext این جدول را اصلاً نمی‌بیند.
     */
    private AccessDecision decidePlatform(Long userId, String permissionCode) {
        if (permissionCode == null || !PlatformPermissions.CODES.contains(permissionCode)) {
            return new AccessDecision.Denied(
                    "Permission '" + permissionCode + "' یک Permission سطح پلتفرم نیست."
            );
        }
        return isPlatformAdmin(userId)
                ? new AccessDecision.Granted("کاربر مدیر پلتفرم است.")
                : new AccessDecision.Denied("کاربر مدیر پلتفرم نیست.");
    }
}
