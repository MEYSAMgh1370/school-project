-- ---------------------------------------------------------------------
-- بازه‌ی اعتبارِ درس به سال (openspec/changes/academic-year.md، فاز ۱)
-- ---------------------------------------------------------------------
-- فهرستِ درس‌های هر پایه در گذر سال‌ها عوض می‌شود. valid_from_year و
-- valid_until_year «سالِ شروعِ» سال‌های تحصیلی‌اند (مثلاً 1404 برای 1404-1405)،
-- هر دو سر شاملِ بازه؛ خالی یعنی «بدونِ محدودیت». همه‌ی درس‌های موجود خالی
-- می‌مانند (همیشه معتبر)؛ مقدارها را مثل بقیه‌ی کاتالوگ، مهاجرتِ داده می‌گذارد.
-- ردیف‌هایی که از قبل به درس اشاره می‌کنند (نمره، خانه‌ی برنامه) با بسته‌شدنِ
-- بازه تغییر نمی‌کنند.

alter table academic.courses
    add column valid_from_year integer check (valid_from_year between 1300 and 1700),
    add column valid_until_year integer check (valid_until_year between 1300 and 1700),
    add constraint courses_valid_range_ordered
        check (valid_from_year is null or valid_until_year is null or valid_from_year <= valid_until_year);
