-- ---------------------------------------------------------------------
-- جدول‌های نشست وب (openspec/changes/jdbc-backed-web-sessions.md)
-- ---------------------------------------------------------------------
-- ستون‌ها، ایندکس‌ها و قیدها عیناً همان schema-postgresql.sql ای هستند که داخل
-- spring-session-jdbc-4.1.0 منتشر شده، فقط با سبک این مخزن (حروف کوچک و
-- schema صریح) نوشته شده‌اند. پستگرس شناسه‌ی بی‌کوتیشن را به حروف کوچک تبدیل
-- می‌کند، پس iam.spring_session همان جدولی است که Spring Session با
-- %TABLE_NAME% آدرس می‌دهد.
--
-- چرا schema صریح: Spring Session JDBC خالص است و hibernate.default_schema را
-- نمی‌بیند؛ نام بی‌پیشوند از search_path اتصال حل می‌شود که روی این دیتابیس
-- «"$user", public» است، نه iam. پس نام جدول در application.yml هم
-- iam.spring_session داده شده تا به search_path وابسته نباشیم.
--
-- ساخت این جدول‌ها کار Flyway است، نه Spring Session:
-- spring.session.jdbc.initialize-schema روی never است
-- (openspec/decisions/2026-07-30-flyway-schema-ownership.md).
create table iam.spring_session (
    primary_id            char(36) not null,
    session_id            char(36) not null,
    creation_time         bigint   not null,
    last_access_time      bigint   not null,
    max_inactive_interval int      not null,
    expiry_time           bigint   not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on iam.spring_session (session_id);
create index spring_session_ix2 on iam.spring_session (expiry_time);
create index spring_session_ix3 on iam.spring_session (principal_name);

create table iam.spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id)
        references iam.spring_session (primary_id) on delete cascade
);
