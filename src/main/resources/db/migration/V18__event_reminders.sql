-- ---------------------------------------------------------------------
-- یادآوری رویدادهای برنامه‌ی سالانه (openspec/changes/event-reminders.md)
-- ---------------------------------------------------------------------
-- school.calendar_entries.remind_days_before: چند روز پیش از رویداد باید یادآوری شروع شود
-- (۰ = همان روز). null = بدون یادآوری. فقط ردیف‌های EVENT مقدار می‌گیرند؛ این قاعده در لایه‌ی
-- facade بررسی می‌شود و ردیف‌های CLOSED همیشه null می‌مانند.
alter table school.calendar_entries
    add column remind_days_before smallint null
        check (remind_days_before between 0 and 30);

-- school.reminder_roles: نقش‌هایی که مدیر مدرسه برای دریافت یادآوری انتخاب کرده است.
-- نبودنِ ردیف برای یک مدرسه = همه‌ی اعضای فعال (پیش‌فرض). role_code کدِ نقش است (سیستمی یا
-- سفارشیِ همین مدرسه) و عمداً کلید خارجی ندارد چون نقش‌ها متعلق به ماژول iam هستند؛ اعتبارِ کد
-- در لایه‌ی کنترلر با فهرستِ نقش‌های مدرسه سنجیده می‌شود.
create table school.reminder_roles (
    school_id bigint not null references school.schools (id),
    role_code varchar(64) not null,
    primary key (school_id, role_code)
);
