-- محتوای curriculum می‌تواند مالکِ پلتفرم داشته باشد: school_id خالی = سرفصل/سؤال
-- پلتفرمی (برای همه‌ی مدرسه‌ها قابل‌مشاهده). source_code کلید پایدارِ upsert برای
-- ایمپورت است؛ فقط وقتی مقدار دارد یکتاست.

alter table curriculum.topics
    alter column school_id drop not null;
alter table curriculum.topics
    add column source_code varchar(32);
create unique index uq_topics_source_code
    on curriculum.topics (source_code) where source_code is not null;

alter table curriculum.questions
    alter column school_id drop not null;
alter table curriculum.questions
    add column source_code varchar(64);
create unique index uq_questions_source_code
    on curriculum.questions (source_code) where source_code is not null;
