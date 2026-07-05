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
     * ثبت‌نام عمومی (self-service): برخلاف {@link #findOrCreateUser} که برای
     * جریان‌های ادمین‌محور است و کاربرِ بدون رمز می‌سازد، این متد کاربری
     * می‌سازد که از همان ابتدا رمز عبور دارد و می‌تواند وارد شود. کاربر
     * تازه‌ثبت‌نام‌شده هیچ Membershipای ندارد - می‌تواند بعداً مدرسه بسازد
     * (و SCHOOL_ADMIN شود) یا به یک مدرسه دعوت شود.
     * <p>
     * نکته‌ی امنیتی: اگر ایمیل از قبل وجود داشته باشد - حتی به‌صورت حسابِ
     * دعوت‌شده‌ی فعال‌نشده (passwordHash=null) - ثبت‌نام رد می‌شود. تصاحب
     * چنین حسابی فقط از مسیر توکن دعوتِ ایمیل‌شده مجاز است (که مالکیت
     * ایمیل را اثبات می‌کند)، وگرنه هر کسی با دانستن ایمیلِ یک
     * دانش‌آموز/معلم موجود می‌توانست حساب او را تصاحب کند.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.InvalidRegistration اگر ایمیل/نام معتبر نباشد،
     * IamError.InvalidPassword اگر رمز عبور کوتاه‌تر از حد مجاز باشد، یا
     * IamError.EmailAlreadyRegistered اگر ایمیل از قبل ثبت شده باشد
     */
    UserId registerUser(String email, String fullName, String password);

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
     * همه‌ی Membershipهای یک کاربر در همه‌ی مدرسه‌ها (شامل غیرفعال‌ها) -
     * همان endpointی که Open Question 2 در CLAUDE.md پیش‌بینی کرده بود
     * («list my memberships» برای داشبورد/سوییچر). خودِ UX سوییچ بین
     * مدرسه‌ها هنوز Open Question است؛ این متد فقط داده‌ی لازم را می‌دهد.
     */
    List<MembershipInfo> findMembershipsByUser(UserId userId);

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
     * صدور یک توکن دعوت (فعال‌سازی حساب) برای کاربر پشت این Membership.
     * اگر کاربر از قبل passwordHash دارد (یعنی قبلاً فعال شده)، هیچ کاری
     * انجام نمی‌شود و Optional.empty() برگردانده می‌شود - این حالت خطا
     * نیست، بلکه یک no-op idempotent است. اگر یک توکن معتبر و redeem-نشده
     * از قبل برای این کاربر وجود داشته باشد، همان دوباره برگردانده می‌شود.
     * <p>
     * پس از صدور (یا استفاده‌ی مجدد از توکن موجود)، لینک فعال‌سازی به ایمیل
     * کاربر فرستاده می‌شود - یعنی صدور مجدد، همان «ارسال دوباره‌ی ایمیل
     * دعوت» است. ارسال best-effort است: اگر شکست بخورد، توکن همچنان
     * برگردانده می‌شود و {@code emailSent=false} به ادمین می‌گوید که باید
     * لینک را دستی تحویل دهد.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.MembershipIdNotFound اگر چنین Membershipای وجود نداشته باشد
     */
    Optional<InviteIssued> issueInviteToken(Long membershipId);

    /**
     * تکمیل فرآیند فعال‌سازی حساب: توکن را معتبر می‌سنجد و در صورت موفقیت
     * passwordHash کاربر را با رمز عبور تازه تنظیم می‌کند.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.InviteTokenNotFound اگر توکن وجود نداشته باشد،
     * IamError.InviteTokenAlreadyRedeemed اگر قبلاً استفاده شده باشد،
     * IamError.InviteTokenExpired اگر منقضی شده باشد، یا
     * IamError.InvalidPassword اگر رمز عبور تازه معتبر نباشد
     */
    void redeemInviteToken(String token, String newPassword);

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
            String email,
            String fullName,
            Long schoolId,
            String roleCode,
            boolean active
    ) {
    }

    record InviteIssued(String token, boolean emailSent) {
    }
}
