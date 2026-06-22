package com.example.roshdeandishe.iam;

import java.util.Optional;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول IAM.
 * <p>
 * سایر ماژول‌ها (academic, finance) فقط از طریق این اینترفیس حق فراخوانی
 * منطق ماژول iam را دارند. کلاس‌های پیاده‌سازی، Entityها و Repositoryهای واقعی
 * در {@code com.example.roshdeandishe.iam.internal} قرار دارند و خارج از این
 * ماژول قابل‌دیدن و فراخوانی نیستند (Spring Modulith این محدودیت را در زمان
 * تست با {@code ApplicationModules.verify()} اعتبارسنجی می‌کند).
 * <p>
 * نسخه‌ی قبلی این Facade فقط {@code isModuleEnabled(schoolId, moduleName)}
 * داشت که فرض می‌کرد همه‌چیز مدرسه‌محور است. نسخه‌ی فعلی صراحتاً به سوال
 * عمومی‌تر پاسخ می‌دهد: "آیا کاربر X به ماژول Y در context Z دسترسی دارد؟"
 * - چه آن context یک مدرسه خاص باشد (B2B) چه نباشد (B2C / پلتفرم).
 */
public interface IamFacade {

    /**
     * شناسه‌ی کاربر احراز هویت‌شده‌ی فعلی (از روی SecurityContext).
     * این متد عمداً جدا از {@code checkAccess} است چون {@code ModuleAccessAspect}
     * نیاز دارد قبل از بررسی دسترسی، اول بداند "کاربر فعلی" کیست.
     *
     * @throws com.example.roshdeandishe.iam.shared.IamException اگر کاربری
     * احراز هویت نشده باشد یا در سیستم پیدا نشود (با IamError.UserNotFound)
     */
    UserId currentUserId();

    /**
     * هسته‌ی اصلی مدل Entitlement جدید. این متد جایگزین مستقیم
     * {@code isModuleEnabled(schoolId, moduleName)} نسخه‌ی قبلی شده.
     * <p>
     * توجه: این متد عمداً {@code userId} را به‌صورت پارامتر می‌گیرد (نه فقط
     * کاربر فعلی) تا در آینده سناریوهایی مثل "ادمین، دسترسی کاربر دیگری را
     * بررسی می‌کند" هم بدون تغییر امضای متد پشتیبانی شود.
     *
     * @param userId     کاربری که می‌خواهیم دسترسی‌اش را بررسی کنیم
     * @param module     کد ماژول (مثلاً "FINANCE", "AI_TUTOR")
     * @param context    بافت درخواست: مدرسه‌ی خاص یا بدون context مدرسه‌ای
     * @return Granted یا Denied، همراه با دلیل
     */
    AccessDecision checkAccess(UserId userId, ModuleCode module, AccessContext context);

    /**
     * اطلاعات پایه‌ی یک کاربر، بدون افشای Entity داخلی.
     */
    Optional<UserProfile> findUser(UserId userId);

    /**
     * نقش کاربر در یک مدرسه‌ی خاص (اگر عضو باشد). چون role دیگر صفت ثابتِ
     * کاربر نیست، این اطلاعات همیشه باید نسبت‌به یک schoolId خاص خواسته شود.
     * اگر کاربر چند نقش در همان مدرسه داشته باشد، اولین مورد بازگردانده می‌شود؛
     * برای گرفتن همه‌ی نقش‌ها، در فاز بعدی متد {@code findAllMemberships} اضافه شود.
     */
    Optional<MembershipInfo> findMembership(UserId userId, Long schoolId);

    record UserId(Long value) {
    }

    record ModuleCode(String value) {
    }

    record UserProfile(UserId id, String email, String fullName) {
    }

    record MembershipInfo(UserId userId, Long schoolId, String roleCode) {
    }
}
