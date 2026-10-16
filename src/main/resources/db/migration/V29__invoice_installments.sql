-- ---------------------------------------------------------------------
-- اقساط فاکتور شهریه (openspec/changes/tuition-installments.md، بخش دوم)
-- ---------------------------------------------------------------------
-- هر قسط مبلغ، مبلغِ پرداخت‌شده‌ی تخصیص‌یافته (از پرداخت‌های فاکتور، قدیمی‌ترین سررسید اول) و سررسیدِ
-- تقویمی (بدون ساعت) دارد. وضعیتِ قسط ذخیره نمی‌شود؛ هنگام خواندن مشتق می‌شود.
-- seq فقط ترتیبِ نمایش است و عمداً unique نیست: بازشماریِ اقساط داخل یک تراکنش ردیف‌ها را حذف/اضافه
-- می‌کند. کلیدِ خارجی فقط داخل ماژول finance است (school_id بدون کلید خارجی).

create table finance.installments
(
    id          bigserial primary key,
    school_id   bigint         not null,
    invoice_id  bigint         not null references finance.invoices (id),
    seq         int            not null,
    amount      numeric(12, 2) not null check (amount > 0),
    amount_paid numeric(12, 2) not null default 0 check (amount_paid >= 0 and amount_paid <= amount),
    due_date    date           not null
);

create index ix_installments_invoice on finance.installments (invoice_id);
