-- گزارش مشکل سؤال (moshavere-content-import.md، D8-D10، P6): هر کاربرِ مجاز می‌تواند مشکل یک سؤالِ
-- قابل‌مشاهده برای مدرسه‌اش را گزارش کند. گزارش هرگز خودِ سؤال را تغییر نمی‌دهد. گردش وضعیت:
-- OPEN -> REVIEWED -> (FIXED | CLOSED). reporter_user_id و school_id شناسه‌ی ساده‌اند، عمداً بدون کلید
-- خارجی (ماژول curriculum نباید به جدول‌های iam وابسته شود)؛ question_id هم‌ماژول است، پس کلید خارجی دارد.

create table curriculum.question_reports
(
    id               bigserial primary key,
    question_id      bigint       not null references curriculum.questions (id),
    reporter_user_id bigint       not null,
    school_id        bigint       not null,
    report_type      varchar(32)  not null,
    comment          text,
    status           varchar(16)  not null default 'OPEN',
    resolution_note  text,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    constraint ck_question_reports_type
        check (report_type in ('WRONG_ANSWER', 'TYPO', 'UNCLEAR_TEXT', 'OTHER')),
    constraint ck_question_reports_status
        check (status in ('OPEN', 'REVIEWED', 'FIXED', 'CLOSED'))
);

create index ix_question_reports_school_created on curriculum.question_reports (school_id, created_at);
create index ix_question_reports_question on curriculum.question_reports (question_id);
create index ix_question_reports_status on curriculum.question_reports (status);
