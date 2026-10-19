-- تنظیمات مدرسه در یک جدول (school-settings): اقساط، ارتقای خودکار و ربات.
-- ستون‌های قدیمیِ school.schools (installments_enabled از V28، auto_promotion از V11) حفظ و دوگانه‌نویسی می‌شوند.
create table school.school_settings (
    school_id            bigint  primary key references school.schools (id),
    installments_enabled boolean not null default false,
    auto_promotion       boolean not null default false,
    bot_enabled          boolean not null default true
);

-- نقش‌های مجاز برای ربات؛ نبودنِ هر ردیفی برای مدرسه یعنی همه‌ی نقش‌ها.
create table school.bot_roles (
    school_id bigint      not null references school.schools (id),
    role_code varchar(64) not null,
    primary key (school_id, role_code)
);

insert into school.school_settings (school_id, installments_enabled, auto_promotion, bot_enabled)
select id, installments_enabled, auto_promotion, true
from school.schools;
