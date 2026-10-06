-- ---------------------------------------------------------------------
-- حضور و غیاب: فهرستِ دانش‌آموزانِ هر roll (openspec/changes/attendance.md)
-- ---------------------------------------------------------------------
-- «چه کسانی در کلاس بودند» در لحظه‌ی ثبتِ هر roll ذخیره می‌شود، تا ثبت‌نام/انتقالِ بعدی رقمِ روزهای
-- گذشته را عوض نکند. rollِ ساخته‌شده پیش از این مهاجرت ردیفی اینجا ندارد و با فهرستِ زنده‌ی کلاس
-- محاسبه می‌شود. بدونِ کلیدِ خارجیِ بین‌ماژولی (مرزِ ماژول).
create table attendance.roll_students (
    school_id bigint not null,
    class_id bigint not null,
    day date not null,
    student_id bigint not null,
    primary key (class_id, day, student_id)
);
create index roll_students_school_day_idx on attendance.roll_students (school_id, day);
