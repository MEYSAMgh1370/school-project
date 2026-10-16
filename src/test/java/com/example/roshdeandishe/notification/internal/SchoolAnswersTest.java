package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.RecordableClass;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InstallmentStatus;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchoolAnswersTest {

    private static final UserId USER = new UserId(7L);
    private static final SchoolId SCHOOL_ID = new SchoolId(5L);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private FinanceFacade finance;
    private AttendanceFacade attendance;
    private SchoolFacade school;
    private SchoolAnswers answers;
    private final BotContext ctx = new BotContext(1L, USER, "کاربر", List.of(membership("TEACHER")));
    private final BotTarget.Student ward = new BotTarget.Student(5L, 11L, 70L, 3L, "علی رضایی", "مدرسه‌ی نمونه", true);
    private final BotTarget.School target = new BotTarget.School(5L, "مدرسه‌ی نمونه");

    private static MembershipInfo membership(String role) {
        return new MembershipInfo(1L, USER, null, null, 5L, role, true, true);
    }

    @BeforeEach
    void setUp() {
        finance = mock(FinanceFacade.class);
        attendance = mock(AttendanceFacade.class);
        school = mock(SchoolFacade.class);
        answers = new SchoolAnswers(finance, attendance, school,
                Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC));
        when(school.findSchool(SCHOOL_ID)).thenReturn(Optional.of(
                new SchoolInfo(SCHOOL_ID, "مدرسه‌ی نمونه", null, "Asia/Tehran", null, null, null, null)));
        when(school.formatSolarDate(any())).thenAnswer(i -> "تاریخ:" + i.getArgument(0));
    }

    private static InvoiceInfo invoice(long id, String amount, String discount, String paid, InvoiceStatus status) {
        return new InvoiceInfo(id, 5L, 11L, new BigDecimal(amount), new BigDecimal(paid), Instant.EPOCH,
                Instant.EPOCH, status, "1405-1406", new BigDecimal(discount));
    }

    private static InstallmentInfo installment(long id, long invoiceId, String amount, String paid,
                                               LocalDate due, InstallmentStatus status) {
        return new InstallmentInfo(id, invoiceId, 1, new BigDecimal(amount), new BigDecimal(paid), due, status);
    }

    @Test
    void remainingSubtractsDiscountAndPayment() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "10000000", "1000000", "2000000", InvoiceStatus.PARTIAL)));
        String text = answers.answer(BotIntent.INVOICES, ctx, ward);
        assertThat(text).startsWith("«علی رضایی — مدرسه‌ی نمونه»\n").contains("مانده‌ی کل: ۷٬۰۰۰٬۰۰۰ ریال")
                .doesNotContain("سررسید گذشته");
    }

    @Test
    void paidInvoicesAreNotCounted() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "10000000", "0", "10000000", InvoiceStatus.PAID),
                invoice(2, "3000000", "0", "0", InvoiceStatus.UNPAID)));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward)).contains("مانده‌ی کل: ۳٬۰۰۰٬۰۰۰ ریال");
    }

    @Test
    void onlyPaidInvoicesMeansNoOpenInvoices() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "10000000", "0", "10000000", InvoiceStatus.PAID)));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward)).contains("فاکتور بازی ندارد.");
    }

    @Test
    void remainingNeverGoesNegative() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "1000000", "1500000", "0", InvoiceStatus.UNPAID)));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward)).contains("مانده‌ی کل: ۰ ریال");
    }

    @Test
    void overdueCountIsShownOnlyWhenPositive() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "1000000", "0", "0", InvoiceStatus.OVERDUE),
                invoice(2, "1000000", "0", "0", InvoiceStatus.OVERDUE),
                invoice(3, "1000000", "0", "0", InvoiceStatus.UNPAID)));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward))
                .contains("مانده‌ی کل: ۳٬۰۰۰٬۰۰۰ ریال").contains("سررسید گذشته: ۲ فاکتور");
    }

    @Test
    void nextInstalmentIsTheEarliestUnpaid() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "6000000", "0", "1000000", InvoiceStatus.PARTIAL)));
        when(finance.listInstallmentsForInvoices(any(), any())).thenReturn(Map.of(1L, List.of(
                installment(1, 1, "1000000", "1000000", LocalDate.of(2026, 9, 1), InstallmentStatus.PAID),
                installment(3, 1, "2000000", "0", LocalDate.of(2026, 12, 1), InstallmentStatus.UNPAID),
                installment(2, 1, "3000000", "0", LocalDate.of(2026, 11, 1), InstallmentStatus.UNPAID))));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward))
                .contains("قسط بعدی: ۳٬۰۰۰٬۰۰۰ ریال تا تاریخ:2026-11-01");
    }

    @Test
    void noInstalmentsMeansNoNextInstalmentLine() {
        when(finance.listInvoicesForStudents(5L, List.of(11L))).thenReturn(List.of(
                invoice(1, "6000000", "0", "0", InvoiceStatus.UNPAID)));
        when(finance.listInstallmentsForInvoices(any(), any())).thenReturn(Map.of());
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward)).doesNotContain("قسط بعدی");
    }

    @Test
    void eventsRespectReminderRoles() {
        when(school.reminderRoleCodes(SCHOOL_ID)).thenReturn(Optional.of(Set.of("PARENT")));
        assertThat(answers.answer(BotIntent.EVENTS, ctx, target)).contains("رویداد نزدیکی نیست.");
    }

    @Test
    void eventsListWithRelativeDays() {
        when(school.reminderRoleCodes(SCHOOL_ID)).thenReturn(Optional.of(Set.of("TEACHER")));
        when(school.dueReminderEvents(SCHOOL_ID, TODAY)).thenReturn(List.of(
                new ReminderEvent(1L, TODAY, "اردو", 3),
                new ReminderEvent(2L, TODAY.plusDays(1), "جشن", 3),
                new ReminderEvent(3L, TODAY.plusDays(3), "همایش", 5)));
        assertThat(answers.answer(BotIntent.EVENTS, ctx, target)).isEqualTo("«مدرسه‌ی نمونه»\n"
                + "اردو — تاریخ:2026-10-17 (امروز)\n"
                + "جشن — تاریخ:2026-10-18 (فردا)\n"
                + "همایش — تاریخ:2026-10-20 (۳ روز دیگر)");
    }

    @Test
    void eventsWithoutSelectionAreForEveryRoleAndEmptyListSaysNone() {
        when(school.reminderRoleCodes(SCHOOL_ID)).thenReturn(Optional.empty());
        when(school.dueReminderEvents(SCHOOL_ID, TODAY)).thenReturn(List.of());
        assertThat(answers.answer(BotIntent.EVENTS, ctx, target)).contains("رویداد نزدیکی نیست.");
    }

    @Test
    void teacherRollListsOnlyUntakenClasses() {
        when(attendance.listRecordableClasses(5L, 7L, false, TODAY)).thenReturn(List.of(
                new RecordableClass(1L, "هفتم", "الف", 7, false),
                new RecordableClass(2L, "هشتم", "ب", 8, true),
                new RecordableClass(3L, "نهم", "ج", 9, false)));
        String text = answers.answer(BotIntent.TEACHER_ROLL, ctx, target);
        assertThat(text).contains("حضور و غیاب این کلاس‌ها هنوز ثبت نشده: هفتم الف، نهم ج")
                .doesNotContain("هشتم");
    }

    @Test
    void teacherRollAllTaken() {
        when(attendance.listRecordableClasses(5L, 7L, false, TODAY)).thenReturn(List.of(
                new RecordableClass(2L, "هشتم", "ب", 8, true)));
        assertThat(answers.answer(BotIntent.TEACHER_ROLL, ctx, target))
                .contains("حضور و غیاب همه‌ی کلاس‌های امروز ثبت شده است.");
    }

    @Test
    void teacherRollOnClosedDay() {
        when(attendance.listRecordableClasses(5L, 7L, false, TODAY)).thenReturn(List.of());
        when(school.isClosedDay(SCHOOL_ID, TODAY)).thenReturn(true);
        assertThat(answers.answer(BotIntent.TEACHER_ROLL, ctx, target)).contains("امروز روز درسی نیست.");
    }

    @Test
    void facadeErrorBecomesNotAvailableSentence() {
        when(attendance.listRecordableClasses(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenThrow(new AttendanceException(new AttendanceError.SchoolNotFound(5L)));
        assertThat(answers.answer(BotIntent.TEACHER_ROLL, ctx, target))
                .contains("وضعیت حضور و غیاب کلاس‌ها در دسترس نیست.");
        when(finance.listInvoicesForStudents(any(), any()))
                .thenThrow(new com.example.roshdeandishe.finance.shared.FinanceException(
                        new com.example.roshdeandishe.finance.shared.FinanceError.InvoiceNotFound(1L)));
        assertThat(answers.answer(BotIntent.INVOICES, ctx, ward)).contains("اطلاعات شهریه در دسترس نیست.");
    }
}
