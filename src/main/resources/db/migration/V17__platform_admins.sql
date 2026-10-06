-- ---------------------------------------------------------------------
-- مدیران پلتفرم (openspec/changes/yearly-plan.md، فاز C)
-- ---------------------------------------------------------------------
-- iam.platform_admins: کاربرانی که می‌توانند مجوزهای سطح پلتفرم (فعلاً فقط
-- PLATFORM_HOLIDAY_MANAGE) را در PlatformContext بگیرند. این جدول «عضویت مدرسه» نیست و
-- به نقش رزروشده‌ی PLATFORM_ADMIN هم ربطی ندارد؛ هیچ ردیف عضویت یا نقشی هرگز از اینجا
-- خوانده یا ساخته نمی‌شود. تنها نویسنده‌ی این جدول، اجراکننده‌ی راه‌اندازیِ
-- PlatformAdminProvisioner است که آن را با متغیر محیطی PLATFORM_ADMIN_EMAILS هم‌گام می‌کند
-- (هیچ API، ثبت‌نام، دعوت یا seed ای ردیفی اینجا نمی‌سازد).
create table iam.platform_admins (
    user_id bigint primary key references iam.users (id),
    created_at timestamptz not null default now()
);
