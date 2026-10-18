-- سوئیچِ جدا برای پیام‌های خودکارِ ربات (غیبت، فاکتور جدید، یادآوری رویداد)؛ مستقل از bot_enabled
-- که فقط پرسش و پاسخ و دکمه‌ها را کنترل می‌کند. پیش‌فرض روشن تا رفتار فعلی عوض نشود.
alter table school.school_settings
    add column notifications_enabled boolean not null default true;
