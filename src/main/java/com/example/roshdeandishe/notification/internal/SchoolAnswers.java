package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.RecordableClass;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InstallmentStatus;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * پاسخِ پرسش‌های مالی و مدرسه‌محور: شهریه‌ی یک فرزند ({@code INVOICES})، کلاس‌های بدونِ حضور و غیابِ معلم
 * ({@code TEACHER_ROLL}) و رویدادهای نزدیک ({@code EVENTS}). مانده‌ی هر فاکتور
 * {@code max(0, مبلغ − تخفیف − پرداخت)} است؛ فاکتورِ تسویه‌شده فهرست نمی‌شود. «امروز» از منطقه‌ی زمانیِ مدرسه
 * می‌آید. قاعده‌ی نقشِ دریافت‌کننده‌ی رویداد عیناً مثل {@code EventReminderController.upcomingEvents} است. خطای
 * دامنه‌ی facade به «در دسترس نیست» تبدیل و warn لاگ می‌شود. {@code TEACHER_ROLL} فقط برای مدرسه‌ای که ماژول
 * {@code ATTENDANCE} را دارد پاسخ می‌دهد (مثل کارت‌های پیشخوان)؛ وگرنه «در دسترس نیست».
 */
@Component
class SchoolAnswers {

    private static final Logger log = LoggerFactory.getLogger(SchoolAnswers.class);
    private static final Set<InvoiceStatus> OPEN_INVOICE = EnumSet.of(
            InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL, InvoiceStatus.OVERDUE);
    private static final Set<InstallmentStatus> OPEN_INSTALLMENT = EnumSet.of(
            InstallmentStatus.UNPAID, InstallmentStatus.PARTIAL, InstallmentStatus.OVERDUE);

    private static final String ATTENDANCE_MODULE = "ATTENDANCE";

    private final IamFacade iamFacade;
    private final FinanceFacade financeFacade;
    private final AttendanceFacade attendanceFacade;
    private final SchoolFacade schoolFacade;
    private final Clock clock;

    @Autowired
    SchoolAnswers(IamFacade iamFacade, FinanceFacade financeFacade, AttendanceFacade attendanceFacade,
                  SchoolFacade schoolFacade) {
        this(iamFacade, financeFacade, attendanceFacade, schoolFacade, Clock.systemUTC());
    }

    /** برای آزمون: ساعتِ ثابت. */
    SchoolAnswers(IamFacade iamFacade, FinanceFacade financeFacade, AttendanceFacade attendanceFacade,
                  SchoolFacade schoolFacade, Clock clock) {
        this.iamFacade = iamFacade;
        this.financeFacade = financeFacade;
        this.attendanceFacade = attendanceFacade;
        this.schoolFacade = schoolFacade;
        this.clock = clock;
    }

    /** پاسخ {@code intent} برای {@code target}؛ خط اول نامِ دانش‌آموز/مدرسه است. */
    String answer(BotIntent intent, BotContext ctx, BotTarget target) {
        Long schoolId;
        String header;
        if (target instanceof BotTarget.Student s) {
            schoolId = s.schoolId();
            header = BotTexts.studentHeader(s.name(), s.schoolName());
        } else {
            BotTarget.School s = (BotTarget.School) target;
            schoolId = s.schoolId();
            header = BotTexts.schoolHeader(s.schoolName());
        }
        try {
            String body = switch (intent) {
                case INVOICES -> invoices((BotTarget.Student) target);
                case TEACHER_ROLL -> teacherRoll(ctx, schoolId);
                case EVENTS -> events(ctx, schoolId);
                default -> throw new IllegalArgumentException("intent " + intent + " is not a school intent");
            };
            return header + "\n" + body;
        } catch (AttendanceException | FinanceException | SchoolException e) {
            log.warn("Bot intent {} failed for school {}: {}", intent, schoolId, e.getClass().getSimpleName());
            return header + "\n" + switch (intent) {
                case INVOICES -> BotTexts.INVOICES_UNAVAILABLE;
                case TEACHER_ROLL -> BotTexts.ROLL_UNAVAILABLE;
                default -> BotTexts.EVENTS_UNAVAILABLE;
            };
        }
    }

    private static BigDecimal remaining(InvoiceInfo invoice) {
        BigDecimal discount = invoice.discountTotal() == null ? BigDecimal.ZERO : invoice.discountTotal();
        BigDecimal paid = invoice.amountPaid() == null ? BigDecimal.ZERO : invoice.amountPaid();
        return invoice.amount().subtract(discount).subtract(paid).max(BigDecimal.ZERO);
    }

    private String invoices(BotTarget.Student student) {
        Long schoolId = student.schoolId();
        List<InvoiceInfo> open = financeFacade.listInvoicesForStudents(schoolId, List.of(student.studentId()))
                .stream().filter(i -> OPEN_INVOICE.contains(i.status())).toList();
        if (open.isEmpty()) {
            return BotTexts.INVOICES_NONE;
        }
        MoneyText.Currency currency = MoneyText.currencyOf(schoolId);
        BigDecimal total = open.stream().map(SchoolAnswers::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdue = open.stream().filter(i -> i.status() == InvoiceStatus.OVERDUE).count();
        StringBuilder out = new StringBuilder(BotTexts.invoiceTotal(MoneyText.format(total, currency)));
        if (overdue > 0) {
            out.append('\n').append(BotTexts.invoicesOverdue(overdue));
        }
        Map<Long, List<InstallmentInfo>> byInvoice = financeFacade.listInstallmentsForInvoices(
                schoolId, open.stream().map(InvoiceInfo::id).toList());
        Optional<InstallmentInfo> next = byInvoice.values().stream().flatMap(List::stream)
                .filter(i -> OPEN_INSTALLMENT.contains(i.status()) && i.dueDate() != null)
                .min(Comparator.comparing(InstallmentInfo::dueDate).thenComparingInt(InstallmentInfo::seq));
        next.ifPresent(i -> {
            BigDecimal due = i.amount().subtract(i.amountPaid() == null ? BigDecimal.ZERO : i.amountPaid())
                    .max(BigDecimal.ZERO);
            out.append('\n').append(BotTexts.nextInstallment(
                    MoneyText.format(due, currency), schoolFacade.formatSolarDate(i.dueDate())));
        });
        return out.toString();
    }

    private String teacherRoll(BotContext ctx, Long schoolId) {
        boolean entitled = iamFacade.checkAccess(ctx.userId(), new ModuleCode(ATTENDANCE_MODULE),
                new AccessContext.SchoolContext(schoolId)).isGranted();
        if (!entitled) {
            return BotTexts.ROLL_UNAVAILABLE;
        }
        SchoolToday today = SchoolToday.of(schoolFacade, clock, new SchoolId(schoolId));
        if (today.closed()) {
            return BotTexts.HOLIDAY_TODAY;
        }
        if (today.weeklyOff()) {
            return BotTexts.NOT_SCHOOL_DAY;
        }
        List<String> pending = attendanceFacade
                .listRecordableClasses(schoolId, ctx.userId().value(), false, today.date()).stream()
                .filter(c -> !c.taken())
                .map(SchoolAnswers::className)
                .toList();
        return pending.isEmpty() ? BotTexts.ROLL_ALL_DONE : BotTexts.rollPending(pending);
    }

    private static String className(RecordableClass c) {
        String name = c.name() == null ? "" : c.name();
        return c.section() == null || c.section().isBlank() ? name : name + " " + c.section();
    }

    private String events(BotContext ctx, Long schoolId) {
        SchoolId school = new SchoolId(schoolId);
        Optional<Set<String>> selected = schoolFacade.reminderRoleCodes(school);
        if (selected.isPresent() && ctx.rolesIn(schoolId).stream().noneMatch(selected.get()::contains)) {
            return BotTexts.EVENTS_NONE;
        }
        LocalDate today = SchoolToday.of(schoolFacade, clock, school).date();
        List<ReminderEvent> events = schoolFacade.dueReminderEvents(school, today);
        if (events.isEmpty()) {
            return BotTexts.EVENTS_NONE;
        }
        return String.join("\n", events.stream().map(e -> BotTexts.eventLine(e.title(),
                schoolFacade.formatSolarDate(e.day()), ChronoUnit.DAYS.between(today, e.day()))).toList());
    }
}
