package com.example.roshdeandishe.iam;

import java.util.List;
import java.util.Optional;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول IAM.
 * <p>
 * این Facade دو لایه‌ی متفاوت از کنترل دسترسی را پوشش می‌دهد:
 * 1) checkAccess: آیا ماژول برای مدرسه/کاربر فعال است؟ (سطح اشتراک)
 * 2) checkPermission: آیا نقش فعال این کاربر در این مدرسه، اجازه‌ی این
 *    عمل خاص را دارد؟ (سطح نقش/مجوز - دقیق‌تر از سطح ۱)
 * <p>
 * متدهای مدیریت Membership (findMembershipsBySchool, setMembershipActive)
 * دقیقاً همان قابلیتی هستند که اجازه می‌دهند یک مدرسه دسترسی یک دانش‌آموز
 * خاص را غیرفعال کند، بدون اینکه روی بقیه‌ی Membershipهای او اثر بگذارد.
 */
public interface IamFacade {

    UserId currentUserId();

    AccessDecision checkAccess(UserId userId, ModuleCode module, AccessContext context);

    /**
     * بررسی دقیق سطح Permission - مرحله‌ی بعد از checkAccess. مثال:
     * مدرسه ماژول FINANCE را خریده (checkAccess=Granted)، اما این کاربر
     * خاص نقشی ندارد که Permission "FINANCE_MANAGE" را شامل شود
     * (checkPermission=Denied).
     */
    AccessDecision checkPermission(UserId userId, PermissionCode permission, AccessContext context);

    Optional<UserProfile> findUser(UserId userId);

    /**
     * ثبت یک کاربر جدید (idempotent بر اساس email) - گام اول هر فرآیند
     * "ادمین یک معلم/دانش‌آموز جدید را به مدرسه اضافه می‌کند": قبل از اینکه
     * بتوان Membership ساخت، باید یک UserEntity برای آن ایمیل وجود داشته
     * باشد. اگر کاربری با همین ایمیل از قبل وجود داشته باشد (مثلاً همان
     * فرد قبلاً در مدرسه‌ی دیگری ثبت‌نام کرده)، همان UserId موجود
     * بازگردانده می‌شود - نه خطا و نه ردیف تکراری.
     */
    UserId findOrCreateUser(String email, String fullName);

    /**
     * نقش(های) کاربر در یک مدرسه‌ی خاص، شامل وضعیت فعال/غیرفعال هر Membership.
     * اگر کاربر چند نقش در همان مدرسه داشته باشد، اولین مورد بازگردانده می‌شود.
     */
    Optional<MembershipInfo> findMembership(UserId userId, Long schoolId);

    /**
     * همه‌ی Membershipهای یک مدرسه (شامل غیرفعال‌ها) - برای پنل مدیریت
     * مدرسه، جایی که ادمین می‌خواهد لیست کامل اعضا و وضعیت دسترسی‌شان را ببیند.
     */
    List<MembershipInfo> findMembershipsBySchool(Long schoolId);

    /**
     * تخصیص یک نقش به یک کاربر در یک مدرسه‌ی خاص - یعنی همان عملیات
     * "ادمین، فلان معلم را با نقش TEACHER به مدرسه اضافه می‌کند". اگر همین
     * (user, school, role) از قبل وجود داشته باشد، آن Membership موجود
     * دوباره فعال می‌شود (idempotent) به‌جای ساخت ردیف تکراری.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException
     * با IamError.RoleNotFound اگر roleCode معتبر نباشد
     */
    Long createMembership(UserId userId, Long schoolId, String roleCode);

    /**
     * فعال/غیرفعال کردن یک Membership خاص با شناسه‌ی آن. این دقیقاً همان
     * عملیاتی است که "مدرسه می‌خواهد دسترسی یک دانش‌آموز خاص را غیرفعال
     * کند" را پیاده می‌کند - فقط همین یک ردیف (User+School+Role) تغییر
     * می‌کند، نه کل حساب کاربری.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.MembershipIdNotFound اگر چنین Membershipای وجود نداشته باشد
     */
    void setMembershipActive(Long membershipId, boolean active);

    /**
     * فعال یا غیرفعال کردن اشتراک یک مدرسه برای یک ماژول SCHOOL-scoped
     * (مثلاً ادمین پلتفرم، ماژول FINANCE را برای مدرسه‌ی X فعال می‌کند).
     * اگر ردیف Entitlement ای از قبل برای این (schoolId, moduleCode) وجود
     * داشته باشد، فقط active آن به‌روزرسانی می‌شود (idempotent).
     * <p>
     * توجه: این متد عمداً فقط برای ماژول‌های SCHOOL-scoped کار می‌کند.
     * تلاش برای استفاده از آن روی یک ماژول USER-scoped یا PLATFORM-scoped
     * با IamError.ModuleScopeMismatch رد می‌شود - آن دو نوع entitlement
     * مسیر متفاوتی دارند (فعلاً متد معادل برای UserEntitlement ساخته
     * نشده، چون هیچ جریان B2C ای هنوز آن را لازم ندارد).
     */
    void setSchoolEntitlement(Long schoolId, ModuleCode module, boolean active);

    record UserId(Long value) {
    }

    record ModuleCode(String value) {
    }

    record PermissionCode(String value) {
    }

    record UserProfile(UserId id, String email, String fullName) {
    }

    record MembershipInfo(
            Long membershipId,
            UserId userId,
            Long schoolId,
            String roleCode,
            boolean active
    ) {
    }
}
