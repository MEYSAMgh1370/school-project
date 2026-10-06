-- ---------------------------------------------------------------------
-- بازه‌ی برنامه‌ی سالانه‌ی هر مدرسه در هر سال تحصیلی (openspec/changes/yearly-plan.md)
-- ---------------------------------------------------------------------
-- school.school_year_ranges: مدرسه می‌تواند بازه‌ی برنامه‌ی سالانه‌ی خودش را برای یک سال
-- تحصیلی «گسترش» دهد (شروع در یا پیش از ۱ مهر، پایان در یا پس از ۳۱ خرداد)؛ بازه‌ی پیش‌فرض
-- حداقل است و کوچک‌تر نمی‌شود. این قاعده در لایه‌ی facade بررسی می‌شود، چون بازه‌ی پیش‌فرض
-- از start_year مشتق می‌شود و ستونی در دیتابیس ندارد. نبودنِ ردیف = بازه‌ی پیش‌فرض؛
-- ثبت دوباره‌ی بازه‌ی پیش‌فرض ردیف را حذف می‌کند. تعطیلی‌های سراسری همچنان با بازه‌ی
-- پیش‌فرض سنجیده می‌شوند.
create table school.school_year_ranges (
    school_id bigint not null references school.schools (id),
    academic_year_id bigint not null references school.academic_years (id),
    start_date date not null,
    end_date date not null,
    created_at timestamptz not null default now(),
    primary key (school_id, academic_year_id),
    check (start_date <= end_date)
);
