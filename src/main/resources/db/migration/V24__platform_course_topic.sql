-- درسِ پلتفرمی می‌تواند به یک سرفصلِ curriculum اشاره کند (شناسه‌ی ساده؛ عمداً بدون کلید خارجی:
-- ماژول academic نباید به جدول‌های curriculum وابسته شود).

alter table academic.courses
    add column topic_id bigint;
