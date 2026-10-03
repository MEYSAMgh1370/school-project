-- ---------------------------------------------------------------------
-- روز شروع هفته‌ی هر مدرسه (openspec/changes/eu-market-readiness.md، W9)
-- ---------------------------------------------------------------------
-- SAT = هفته‌ی ایرانی: شنبه تا چهارشنبه روز درسی است. MON = دوشنبه تا جمعه.
-- پیش‌فرض SAT است: هر مدرسه‌ی موجود، و هر هفته‌ی ذخیره‌شده‌ی برنامه‌ی مطالعه و آزمون،
-- بدون هیچ تغییری همان‌طور می‌ماند. مدرسه‌ی غیرایرانی در لحظه‌ی ساخت MON را انتخاب
-- می‌کند؛ تغییرِ آن بعد از ساخت عمداً هنوز وجود ندارد (هفته‌های ذخیره‌شده را بی‌معنا می‌کند).
alter table school.schools
    add column week_start_day varchar(3) not null default 'SAT'
        constraint schools_week_start_day_check check (week_start_day in ('SAT', 'MON'));

-- برنامه‌ی کلاسی: هفته‌ی دوشنبه‌محور پنج‌شنبه و جمعه را هم لازم دارد. فقط گسترش
-- مجموعه‌ی مجاز است؛ هر ردیف موجود معتبر می‌ماند. اینکه یک مدرسه کدام روزها را
-- دارد را AcademicFacade با SchoolFacade می‌سنجد، نه این constraint.
alter table academic.timetable_entries
    drop constraint timetable_entries_weekday_check;
alter table academic.timetable_entries
    add constraint timetable_entries_weekday_check
        check (weekday in ('SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI'));
