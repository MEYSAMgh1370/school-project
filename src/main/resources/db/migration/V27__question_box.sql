-- باکس سؤالات (openspec/changes/question-box.md): سؤال‌های معلم (چهارگزینه‌ای و تشریحی) با تصویر، سربرگ‌ها و
-- برگه‌های چاپی. school_id، created_by_user_id و bank_question_id شناسه‌ی ساده‌اند، عمداً بدون کلید خارجی
-- (ماژول questionbox به جدول‌های iam/school/curriculum وابسته نمی‌شود)؛ ارجاع‌های درون ماژول کلید خارجی دارند.

create schema if not exists questionbox;

create table questionbox.questions
(
    id                 bigserial primary key,
    school_id          bigint       not null,
    created_by_user_id bigint       not null,
    topic_id           bigint,
    type               varchar(16)  not null,
    content_mode       varchar(16)  not null,
    text               text,
    choice1            text,
    choice2            text,
    choice3            text,
    choice4            text,
    correct_choice     integer,
    answer             text,
    suggested_score    integer,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    deleted_at         timestamptz,
    constraint ck_qb_questions_type check (type in ('MULTIPLE_CHOICE', 'ESSAY')),
    constraint ck_qb_questions_mode check (content_mode in ('TEXT', 'IMAGE_ONLY')),
    constraint ck_qb_questions_correct check (correct_choice is null or correct_choice between 1 and 4)
);

create index ix_qb_questions_school on questionbox.questions (school_id, deleted_at);

create table questionbox.question_images
(
    id           bigserial primary key,
    question_id  bigint      not null references questionbox.questions (id),
    role         varchar(16) not null,
    content_type varchar(64) not null,
    data         bytea       not null,
    created_at   timestamptz not null,
    constraint ck_qb_images_role check (role in ('STEM', 'CHOICE_1', 'CHOICE_2', 'CHOICE_3', 'CHOICE_4', 'ANSWER'))
);

create index ix_qb_images_question on questionbox.question_images (question_id);

create table questionbox.headers
(
    id                 bigserial primary key,
    school_id          bigint       not null,
    created_by_user_id bigint       not null,
    name               varchar(128) not null,
    mode               varchar(16)  not null,
    layout             varchar(16),
    show_school_name   boolean      not null default true,
    show_logo          boolean      not null default false,
    fields             varchar(255) not null default '',
    image_content_type varchar(64),
    image              bytea,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    constraint ck_qb_headers_mode check (mode in ('LAYOUT', 'IMAGE')),
    constraint ck_qb_headers_layout check (layout is null or layout in ('CLASSIC', 'COMPACT', 'BOXED'))
);

create index ix_qb_headers_school on questionbox.headers (school_id);

create table questionbox.sheets
(
    id                       bigserial primary key,
    school_id                bigint       not null,
    created_by_user_id       bigint       not null,
    title                    varchar(255) not null,
    header_id                bigint references questionbox.headers (id),
    header_values            text         not null default '{}',
    show_essay_answer_space  boolean      not null default true,
    include_answer_key       boolean      not null default false,
    created_at               timestamptz  not null,
    updated_at               timestamptz  not null
);

create index ix_qb_sheets_school on questionbox.sheets (school_id);

create table questionbox.sheet_sections
(
    id          bigserial primary key,
    sheet_id    bigint       not null references questionbox.sheets (id) on delete cascade,
    position    integer      not null,
    title       varchar(255) not null,
    instruction text
);

create index ix_qb_sections_sheet on questionbox.sheet_sections (sheet_id, position);

create table questionbox.sheet_items
(
    id               bigserial primary key,
    section_id       bigint  not null references questionbox.sheet_sections (id) on delete cascade,
    position         integer not null,
    box_question_id  bigint references questionbox.questions (id),
    bank_question_id bigint,
    constraint ck_qb_items_one_source check ((box_question_id is null) <> (bank_question_id is null))
);

create index ix_qb_items_section on questionbox.sheet_items (section_id, position);
