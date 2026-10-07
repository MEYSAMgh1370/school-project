-- ---------------------------------------------------------------------
-- دفتر ارسال یادآوری رویدادها (openspec/changes/event-reminders.md)
-- ---------------------------------------------------------------------
-- bale.event_reminders_sent: به ازای هر (رویداد، کاربر) حداکثر یک ردیف. ردیف «قبل از» ارسال
-- با insert ... on conflict do nothing نوشته می‌شود و فقط اگر درج شد پیام می‌رود؛ پس اجرای
-- دوباره‌ی job، ری‌استارت و چند روزِ پنجره‌ی یادآوری هیچ‌وقت پیام تکراری نمی‌سازند و کاربرِ
-- بدونِ حسابِ بله هم دوباره تلاش نمی‌شود. calendar_entry_id و user_id شناسه‌ی عددیِ مات‌اند
-- (بدون FK به schema های school و iam؛ ماژول notification به آن‌ها وابسته‌ی دیتابیسی نیست).
create table bale.event_reminders_sent (
    calendar_entry_id bigint not null,
    user_id bigint not null,
    sent_at timestamptz not null default now(),
    primary key (calendar_entry_id, user_id)
);
