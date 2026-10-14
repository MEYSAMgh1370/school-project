-- ---------------------------------------------------------------------
-- audit کامل‌تر (فاکتور + جزئیاتِ «چه چیزی عوض شد») و قسطِ بدون سررسید
-- ---------------------------------------------------------------------
-- invoice_id: فاکتورِ مربوط (خالی برای اقدام‌های سطح مدرسه/هزینه)؛ عمداً بدون کلید خارجی. details: JSON فشرده‌ی
-- قبل/بعد (متنِ آزاد تا ۵۱۲ نویسه بریده می‌شود). جدول همچنان append-only است.
alter table finance.finance_audit
    add column invoice_id bigint null,
    add column details    text   null;

create index ix_finance_audit_invoice on finance.finance_audit (invoice_id);

-- قسطِ بدون سررسید: وقتی تخفیفِ مشروط برداشته می‌شود و قسطِ پرداخت‌نشده‌ای نمانده، سیستم تاریخ نمی‌سازد؛
-- قسطی با due_date خالی («بدون سررسید») می‌سازد که ادمین بعداً تاریخ می‌زند. ترتیب: NULLها آخر.
alter table finance.installments
    alter column due_date drop not null;

-- رجیستری رویدادهای Spring Modulith (V1) serialized_event را varchar(255) ساخته بود؛ رویدادهای audit اکنون
-- جزئیات دارند (یادداشت پرداخت تا ۵۱۲ نویسه، قبل/بعدِ اقساط) و درج رویداد با «value too long» می‌شکست.
alter table iam.event_publication
    alter column serialized_event type text;
