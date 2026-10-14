-- ---------------------------------------------------------------------
-- تخفیف روی فاکتور شهریه و تنظیم «اقساط» مدرسه (openspec/changes/tuition-installments.md)
-- ---------------------------------------------------------------------
-- installments_enabled: ادمین مدرسه اقساط را روشن می‌کند؛ پیش‌فرض خاموش.
-- discount_total: جمعِ نگه‌داشته‌شده‌ی تخفیف‌های فاکتور (مثل amount_paid) تا «مبلغ قابل پرداخت =
-- amount - discount_total» بدون aggregate کردن جدول تخفیف‌ها مشتق شود. ردیف‌های موجود صفر می‌گیرند.
-- invoice_discounts: append-only؛ کلیدِ خارجی فقط داخل ماژول finance (school_id بدون کلید خارجی).

alter table school.schools
    add column installments_enabled boolean not null default false;

alter table finance.invoices
    add column discount_total numeric(12, 2) not null default 0,
    add constraint ck_invoices_discount_total check (discount_total >= 0);

create table finance.invoice_discounts
(
    id                 bigserial primary key,
    school_id          bigint         not null,
    invoice_id         bigint         not null references finance.invoices (id),
    amount             numeric(12, 2) not null check (amount > 0),
    reason             varchar(255),
    created_by_user_id bigint,
    created_at         timestamptz    not null
);

create index ix_invoice_discounts_invoice on finance.invoice_discounts (invoice_id);
