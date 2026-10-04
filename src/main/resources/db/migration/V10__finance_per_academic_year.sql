-- ---------------------------------------------------------------------
-- شهریه و فاکتور به‌تفکیک سال تحصیلی (openspec/changes/academic-year.md، فاز ۲)
-- ---------------------------------------------------------------------
-- academic_year: کدِ canonical سال («1405-1406») مثل نمره و برنامه‌ی کلاسی؛ بدون کلیدِ خارجی
-- (فهرستِ سال‌ها در ماژول school است و finance به جدول آن وصل نمی‌شود). خالی (NULL) یعنی
-- «به سالی نسبت داده نشده»: همه‌ی ردیف‌های موجود و ردیف‌های مدرسه‌ای که سالِ جاری ندارد - هیچ
-- سالی حدس زده نمی‌شود؛ ادمین با یک اقدامِ صریح آن‌ها را به یک سال نسبت می‌دهد.
--
-- یکتایی: شهریه یکی به‌ازای (مدرسه، سال) و یکی به‌ازای «بدون سال»؛ فاکتور یکی به‌ازای
-- (مدرسه، دانش‌آموز، سال) و یکی به‌ازای (مدرسه، دانش‌آموز) بدون سال - ایندکس‌های جزئی جایگزین
-- unique(school_id) و uq_invoices_school_student می‌شوند.

alter table finance.tuition_fees add column academic_year varchar(16);
alter table finance.invoices add column academic_year varchar(16);

-- unique(school_id) روی tuition_fees: نامِ constraint در دیتابیس‌هایی که از قبل با ddl-auto=update
-- ساخته شده‌اند با نامِ V1 فرق دارد، پس از روی ستون‌هایش پیدا می‌شود (همان الگوی V5)
do $$
declare
    existing record;
begin
    for existing in
        select con.conname
        from pg_constraint con
                 join pg_class rel on rel.oid = con.conrelid
                 join pg_namespace ns on ns.oid = rel.relnamespace
        where ns.nspname = 'finance'
          and rel.relname = 'tuition_fees'
          and con.contype = 'u'
          and (select array_agg(att.attname::text order by att.attname)
               from unnest(con.conkey) as key (attnum)
                        join pg_attribute att on att.attrelid = rel.oid and att.attnum = key.attnum)
              = array ['school_id']
        loop
            execute format('alter table finance.tuition_fees drop constraint %I', existing.conname);
        end loop;
end
$$;

alter table finance.invoices drop constraint uq_invoices_school_student;

create unique index uq_tuition_fees_school_year
    on finance.tuition_fees (school_id, academic_year) where academic_year is not null;
create unique index uq_tuition_fees_school_unattributed
    on finance.tuition_fees (school_id) where academic_year is null;

create unique index uq_invoices_school_student_year
    on finance.invoices (school_id, student_id, academic_year) where academic_year is not null;
create unique index uq_invoices_school_student_unattributed
    on finance.invoices (school_id, student_id) where academic_year is null;
