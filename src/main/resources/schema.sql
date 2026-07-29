-- ساخت schemaهای هر ماژول، قبل از اینکه Hibernate جدول‌ها را بسازد.
--
-- چرا لازم است: ddl-auto=update فقط جدول‌ها را می‌سازد، نه خودِ schema را
-- (کامنت بالای application.yml). تا پیش از این، این هشت دستور باید دستی روی
-- دیتابیس اجرا می‌شد؛ روی یک دیتابیسِ managed (مثل Postgres لیارا) این یعنی
-- اولین بالا آمدن اپلیکیشن حتماً fail می‌شود مگر اینکه کسی یادش باشد اول
-- psql بزند. تست‌ها همین کار را از قبل با init-schemas.sql انجام می‌دادند
-- (TestcontainersConfiguration) - این فایل همان را برای اجرای واقعی فراهم
-- می‌کند.
--
-- Spring Boot این اسکریپت را در زمان مقداردهی اولیه‌ی DataSource اجرا
-- می‌کند، یعنی *قبل از* ساخته شدن EntityManagerFactory - پس Hibernate همیشه
-- schemaها را موجود می‌بیند. با spring.sql.init.mode=always فعال است.
--
-- IF NOT EXISTS یعنی هر بار بالا آمدن اپلیکیشن بی‌خطر است (idempotent).
-- توجه: کاربر دیتابیس باید مجوز CREATE روی دیتابیس داشته باشد؛ اگر نداشت،
-- همین هشت خط را یک بار دستی با یک کاربر مجاز اجرا کنید (رجوع به
-- DEPLOYMENT.md).
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS school;
CREATE SCHEMA IF NOT EXISTS academic;
CREATE SCHEMA IF NOT EXISTS finance;
CREATE SCHEMA IF NOT EXISTS bale;
CREATE SCHEMA IF NOT EXISTS curriculum;
CREATE SCHEMA IF NOT EXISTS studyplan;
CREATE SCHEMA IF NOT EXISTS exam;
