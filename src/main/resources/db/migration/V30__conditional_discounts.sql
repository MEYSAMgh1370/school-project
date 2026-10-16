-- ---------------------------------------------------------------------
-- تخفیفِ مشروط: تخفیفی با مهلت (openspec/changes/tuition-installments.md، ضمیمه‌ی «تخفیف مشروط»)
-- ---------------------------------------------------------------------
-- deadline: آخرِ روزِ مهلت به وقتِ مدرسه؛ خالی = تخفیفِ بی‌قیدوشرط.
-- status: APPLIED (بی‌قیدوشرط)، PENDING (مشروطِ باز)، EARNED (شرط برآورده شد، دائمی)، LAPSED (مهلت گذشت).
-- invoices.discount_total همچنان «تخفیف‌های برقرار» است (APPLIED + PENDING + EARNED)؛ فقط LAPSED آن را کم می‌کند.
-- ردیف‌های موجود APPLIED می‌شوند. resolved_at لحظه‌ی EARNED یا LAPSED شدن است.

alter table finance.invoice_discounts
    add column deadline    date,
    add column status      varchar(8) not null default 'APPLIED',
    add column resolved_at timestamptz,
    add constraint ck_invoice_discounts_status check (status in ('APPLIED', 'PENDING', 'EARNED', 'LAPSED'));

create index ix_invoice_discounts_pending_deadline on finance.invoice_discounts (deadline)
    where status = 'PENDING';
