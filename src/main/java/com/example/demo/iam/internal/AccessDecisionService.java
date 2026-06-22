package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.shared.IamError;
import com.example.roshdeandishe.iam.shared.IamException;
import org.springframework.stereotype.Service;

/**
 * این کلاس مستقیماً به سوال معماری جدید پاسخ می‌دهد:
 * "آیا کاربر X به ماژول Y در context Z دسترسی دارد؟"
 * <p>
 * بسته به {@code ModuleEntity.scope}، منبع بررسی فرق می‌کند:
 * - SCHOOL  -> {@code SchoolEntitlementRepository} (بر اساس schoolId از context)
 * - USER    -> {@code UserEntitlementRepository} (بر اساس خودِ userId، مستقل از context)
 * - PLATFORM -> همیشه مجاز، بدون نیاز به جدول Entitlement
 * <p>
 * چون {@code ModuleScope} یک enum بسته (در عمل sealed-like) است،
 * switch expression زیر بدون default-branch کامپایل می‌شود و با اضافه شدن
 * یک scope جدید در آینده، کامپایلر این متد را به خطا می‌دهد تا حتماً
 * پوشش داده شود.
 */
@Service
class AccessDecisionService {

    private final ModuleRepository moduleRepository;
    private final SchoolEntitlementRepository schoolEntitlementRepository;
    private final UserEntitlementRepository userEntitlementRepository;

    AccessDecisionService(
            ModuleRepository moduleRepository,
            SchoolEntitlementRepository schoolEntitlementRepository,
            UserEntitlementRepository userEntitlementRepository
    ) {
        this.moduleRepository = moduleRepository;
        this.schoolEntitlementRepository = schoolEntitlementRepository;
        this.userEntitlementRepository = userEntitlementRepository;
    }

    AccessDecision decide(Long userId, String moduleCode, AccessContext context) {
        ModuleEntity module = moduleRepository.findByCode(moduleCode)
                .orElseThrow(() -> new IamException(new IamError.ModuleNotFound(moduleCode)));

        return switch (module.getScope()) {
            case PLATFORM -> new AccessDecision.Granted("ماژول در سطح پلتفرم و برای همه باز است.");
            case SCHOOL -> decideForSchoolScope(module, context);
            case USER -> decideForUserScope(module, userId);
        };
    }

    private AccessDecision decideForSchoolScope(ModuleEntity module, AccessContext context) {
        if (!(context instanceof AccessContext.SchoolContext schoolContext)) {
            return new AccessDecision.Denied(
                    "ماژول '" + module.getCode() + "' مدرسه‌محور است اما هیچ context مدرسه‌ای ارسال نشده " +
                    "(هدر X-School-ID موجود نیست)."
            );
        }

        boolean active = schoolEntitlementRepository
                .findBySchoolIdAndModuleIdAndActiveTrue(schoolContext.schoolId(), module.getId())
                .isPresent();

        return active
                ? new AccessDecision.Granted("مدرسه " + schoolContext.schoolId() + " اشتراک فعال دارد.")
                : new AccessDecision.Denied(
                        "مدرسه " + schoolContext.schoolId() + " اشتراک فعالی برای '" + module.getCode() + "' ندارد."
                  );
    }

    private AccessDecision decideForUserScope(ModuleEntity module, Long userId) {
        boolean active = userEntitlementRepository
                .findByUserIdAndModuleIdAndActiveTrue(userId, module.getId())
                .isPresent();

        return active
                ? new AccessDecision.Granted("کاربر " + userId + " اشتراک شخصی فعال دارد.")
                : new AccessDecision.Denied(
                        "کاربر " + userId + " اشتراک شخصی فعالی برای '" + module.getCode() + "' ندارد."
                  );
    }
}
