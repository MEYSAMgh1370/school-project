package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.internal.BaleClient.InlineButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * مسیریابِ بات: متنِ دکمه یا callback را به یک {@link BotIntent} و پاسخِ کلاسِ مربوط می‌رساند و منوی
 * متناسب با نقشِ کاربر را می‌سازد. امنیت: {@code userId} فقط از {@link BotContext} (لینکِ بله) می‌آید و
 * شناسه‌ی داخل callback فقط «انتخاب» است؛ همیشه باید در میانِ {@link SubjectResolver#targets} همین کاربر
 * پیدا شود، وگرنه {@link BotTexts#NOT_AVAILABLE} و هیچ کلاسِ پاسخی صدا زده نمی‌شود.
 */
@Component
class BotRouter {

    private static final Logger log = LoggerFactory.getLogger(BotRouter.class);

    /** پاسخ؛ {@code choices} خالی یعنی پاسخِ ساده، وگرنه دکمه‌های اینلاینِ انتخاب. */
    record Reply(String text, List<List<InlineButton>> choices) {
        static Reply plain(String text) {
            return new Reply(text, List.of());
        }
    }

    private final SubjectResolver resolver;
    private final StudentAnswers studentAnswers;
    private final TimetableAnswers timetableAnswers;
    private final SchoolAnswers schoolAnswers;

    BotRouter(SubjectResolver resolver, StudentAnswers studentAnswers,
              TimetableAnswers timetableAnswers, SchoolAnswers schoolAnswers) {
        this.resolver = resolver;
        this.studentAnswers = studentAnswers;
        this.timetableAnswers = timetableAnswers;
        this.schoolAnswers = schoolAnswers;
    }

    /** فقط دکمه‌هایی که intentشان دست‌کم یک هدف دارد، دوتا در هر ردیف، و «راهنما» آخر. */
    List<List<String>> menu(BotContext ctx) {
        // یک بررسیِ ارزان برای هر نوعِ هدف (نه برای هر intent)؛ بدون خواندنِ نام‌ها
        java.util.Map<BotIntent.TargetKind, Boolean> hasAny = new java.util.EnumMap<>(BotIntent.TargetKind.class);
        List<String> buttons = new ArrayList<>();
        for (BotIntent intent : BotIntent.values()) {
            if (hasAny.computeIfAbsent(intent.targetKind(), k -> resolver.hasAny(ctx, k))) {
                buttons.add(intent.button());
            }
        }
        buttons.add(BotTexts.BTN_HELP);
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 2) {
            rows.add(List.copyOf(buttons.subList(i, Math.min(i + 2, buttons.size()))));
        }
        return rows;
    }

    /** پاسخ به متنِ یک دکمه‌ی منو؛ null یعنی متن هیچ intentی نیست. */
    Reply onText(BotContext ctx, String text) {
        Optional<BotIntent> intent = BotIntent.byButton(text);
        if (intent.isEmpty()) {
            return null;
        }
        List<BotTarget> targets = resolver.targets(ctx, intent.get());
        if (targets.isEmpty()) {
            return Reply.plain(BotTexts.NOT_AVAILABLE);
        }
        if (targets.size() == 1) {
            return answer(intent.get(), ctx, targets.get(0));
        }
        return choose(intent.get(), targets);
    }

    /** پاسخ به callback؛ id باید در هدف‌های همین کاربر باشد. */
    Reply onCallback(BotContext ctx, String data) {
        Optional<CallbackCodec.Decoded> decoded = CallbackCodec.decode(data);
        if (decoded.isEmpty()) {
            return Reply.plain(BotTexts.NOT_AVAILABLE);
        }
        BotIntent intent = decoded.get().intent();
        long id = decoded.get().id();
        return resolver.targets(ctx, intent).stream()
                .filter(t -> t.id() == id)
                .findFirst()
                .map(t -> answer(intent, ctx, t))
                .orElseGet(() -> Reply.plain(BotTexts.NOT_AVAILABLE));
    }

    private Reply choose(BotIntent intent, List<BotTarget> targets) {
        boolean students = targets.get(0) instanceof BotTarget.Student;
        List<List<InlineButton>> rows = new ArrayList<>();
        for (BotTarget target : targets) {
            String label = switch (target) {
                case BotTarget.Student s -> BotTexts.studentChoice(s.name(), s.schoolName());
                case BotTarget.School s -> s.schoolName();
            };
            rows.add(List.of(new InlineButton(label, CallbackCodec.encode(intent, target))));
        }
        return new Reply(students ? BotTexts.CHOOSE_STUDENT : BotTexts.CHOOSE_SCHOOL, rows);
    }

    private Reply answer(BotIntent intent, BotContext ctx, BotTarget target) {
        try {
            String text = switch (intent) {
                case ATTENDANCE_TODAY, ATTENDANCE_SUMMARY, GRADES, EXAMS ->
                        studentAnswers.answer(intent, ctx, target);
                case TIMETABLE_TODAY, TEACHER_TODAY -> timetableAnswers.answer(intent, ctx, target);
                case INVOICES, TEACHER_ROLL, EVENTS -> schoolAnswers.answer(intent, ctx, target);
            };
            return Reply.plain(text);
        } catch (RuntimeException e) {
            // فقط شناسه‌ها و نوعِ استثنا؛ نه پیام (ممکن است داده‌ی کاربر داشته باشد)
            log.warn("Bot answer failed: intent={} userId={} targetId={} error={}",
                    intent.code(), ctx.userId().value(), target.id(), e.getClass().getSimpleName());
            return Reply.plain(BotTexts.NOT_AVAILABLE);
        }
    }
}
