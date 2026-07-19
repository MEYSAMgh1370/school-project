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

    /**
     * اعطای بسته‌ی رایگان ماژول‌ها به یک مدرسه‌ی تازه‌ساخته - تصمیم صریح
     * محصول (CLAUDE.md §11 Q10، حل‌شده 2026-07-20): ACADEMIC و FINANCE
     * رایگان‌اند و در همان لحظه‌ی ساخت خودسرویسی مدرسه فعال می‌شوند؛
     * CURRICULUM/STUDY_PLANNER/EXAM پولی‌اند و تا وقتی billing واقعی ساخته
     * نشده فقط از طریق همین facade (setSchoolEntitlement، بدون REST -
     * CLAUDE.md §10) قابل اعطا هستند.
     * <p>
     * فهرست ماژول‌های رایگان اینجا در iam نگه داشته می‌شود (مالک مفهوم
     * Entitlement)، نه در school - وقتی billing واقعی برسد فقط همین یک
     * نقطه باید عوض شود. idempotent (روی setSchoolEntitlement سوار است).
     */
    void grantFreeTierEntitlements(Long schoolId);

    /**
     * پیدا کردن کاربر بر اساس شماره تلفن - مسیر اصلی «اتصال دوباره»ی ربات
     * بله: کاربری که قبلاً شماره‌اش را (وقتی با سشن معتبر وارد بود) ثبت
     * کرده، دفعه‌ی بعد فقط با اشتراک‌گذاری مخاطب در بله شناسایی می‌شود.
     */
    Optional<UserProfile> findUserByPhoneNumber(String phoneNumber);

    /**
     * ثبت/به‌روزرسانی شماره تلفن کاربر فعلی. عمداً هیچ مسیر ورود جدیدی باز
     * نمی‌کند - این متد فقط برای کاربری صدا زده می‌شود که از قبل با سشن
     * معتبر وارد شده (مثلاً از طریق {@code PUT /api/me/phone})؛ شماره تلفن
     * صرفاً یک کانال ارتباطی اضافه به همان حساب است، نه یک راه جدید برای
     * اثبات هویت. Idempotent برای همان کاربر است.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.InvalidPhoneNumber اگر فرمت شماره نامعتبر باشد، یا
     * IamError.PhoneNumberAlreadyClaimed اگر همین شماره قبلاً برای کاربر
     * دیگری ثبت شده باشد
     */
    void setPhoneNumber(UserId userId, String phoneNumber);

    /**
     * فهرست کامل Permissionهای ثابت سیستم - برای ساخت چک‌لیستِ صفحه‌ی
     * ساخت نقش سفارشی (یک مدرسه فقط می‌تواند از میان همین مجموعه انتخاب
     * کند، نه اینکه کد Permission تازه‌ای اختراع کند - وگرنه گرنت کردنش
     * عملاً هیچ @RequiresPermission ای را کنترل نمی‌کند).
     */
    List<PermissionInfo> listPermissions();

    /**
     * همه‌ی نقش‌های قابل‌مشاهده برای یک مدرسه: نقش‌های سیستمی (schoolId=null
     * در PermissionInfo، ثابت برای همه‌ی مدرسه‌ها) + نقش‌های سفارشیِ خودِ
     * همین مدرسه. PLATFORM_ADMIN عمداً حذف می‌شود - مسیر بررسی مجوز ندارد
     * (CLAUDE.md §7 مورد ۱) و نباید در هیچ UI ای ظاهر شود (همان قرارداد
     * web.RoleLabels).
     */
    List<RoleInfo> listRoles(Long schoolId);

    /**
     * ساخت یک نقش سفارشی برای یک مدرسه‌ی خاص - حل‌کننده‌ی Open Question 3
     * در CLAUDE.md، با سه تصمیم صریح (۲۰۲۶-۰۷-۱۳): (۱) نقش سفارشی فقط برای
     * همین مدرسه قابل‌مشاهده/تخصیص است، نه سراسری - دو مدرسه‌ی مختلف
     * می‌توانند بدون تداخل هر دو یک کدِ یکسان بسازند؛ (۲) permissionCodes
     * فقط می‌تواند از میان Permissionهای موجود سیستم انتخاب شود، نه کدهای
     * تازه‌اختراع‌شده؛ (۳) نقش‌های سیستمی (SCHOOL_ADMIN و بقیه) از این مسیر
     * قابل‌ویرایش نیستند - این متد فقط نقش تازه می‌سازد.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.InvalidRoleDefinition اگر code/displayName نامعتبر باشد یا
     * permissionCodes خالی باشد، IamError.RoleCodeAlreadyExists اگر code با
     * یک نقش سیستمی یا نقش سفارشیِ همین مدرسه تداخل داشته باشد، یا
     * IamError.InvalidPermissionCode اگر یکی از permissionCodes در سیستم
     * تعریف نشده باشد
     */
    RoleInfo createCustomRole(Long schoolId, String code, String displayName, List<String> permissionCodes);

    /**
     * درخواست یک کد تأیید پیامکی برای شماره‌ی از قبل ثبت‌شده - فاکتور دوم
     * ورود، نه یک مسیر ثبت‌نام جدید. عمداً هیچ کاربری با این متد ساخته
     * نمی‌شود؛ شماره تلفن فقط از طریق {@link #setPhoneNumber} (با سشن معتبر)
     * قابل ثبت است - همان مرز امنیتی‌ای که آن متد از قبل دارد.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.PhoneNotRegistered اگر هیچ کاربری با این شماره ثبت نشده باشد
     */
    void requestOtp(String phoneNumber);

    /**
     * اعتبارسنجی کد تأیید پیامکی. فقط آخرین کدِ مصرف‌نشده‌ی کاربر بررسی
     * می‌شود (رجوع به VerificationCodeRepository) - درخواست یک کد تازه،
     * کدهای قبلی را عملاً بی‌اثر می‌کند. این متد فقط دامنه را اعتبارسنجی و
     * کد را مصرف‌شده علامت می‌زند؛ برقراری Authentication/Session یا صدور
     * JWT برعهده‌ی caller است (همان تفکیکی که AuthController.login/authenticate
     * از قبل بین اعتبارسنجی credential و برقراری session دارد) - این Facade
     * از SecurityContext/HttpSession چیزی نمی‌داند.
     *
     * @throws com.example.roshdeandishe.iam.shared.exception.IamException با
     * IamError.PhoneNotRegistered اگر هیچ کاربری با این شماره ثبت نشده باشد،
     * IamError.OtpExpired اگر کد فعالی برای این کاربر وجود نداشته باشد یا
     * منقضی شده باشد، یا IamError.OtpIncorrect اگر کد وارد‌شده نادرست باشد
     */
    UserProfile verifyOtp(String phoneNumber, String code);

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

    record PermissionInfo(Long id, String code, String displayName) {
    }

    record RoleInfo(Long id, String code, String displayName, Long schoolId, List<String> permissionCodes) {
    }
}
