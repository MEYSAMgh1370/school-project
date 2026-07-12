package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.ExpenseFacade;
import com.example.roshdeandishe.finance.ExpenseFacade.CategorySummary;
import com.example.roshdeandishe.finance.ExpenseFacade.FinanceSummary;
import com.example.roshdeandishe.finance.ExpenseFacade.MethodSummary;
import com.example.roshdeandishe.finance.ExpenseFacade.VendorSummary;
import com.example.roshdeandishe.finance.ExpenseStatus;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * گزارش‌های هزینه (ExpenseFacade summaryBy.../financeSummary) - قاعده‌ی ثابت
 * "فقط APPROVED و PAID حساب می‌شوند" (DRAFT و CANCELLED نادیده گرفته
 * می‌شوند)، بازه‌ی تاریخ، گروه‌بندی دسته/طرف‌حساب (با نام حل‌شده‌ی سمت
 * سرور)/روش پرداخت، و هزینه‌ی بدون طرف‌حساب.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpenseReportFlowIntegrationTest {

    @Autowired
    private ExpenseFacade expenseFacade;

    @Autowired
    private FinanceFacade financeFacade;

    private long approvedExpense(Long schoolId, LocalDate date, BigDecimal amount, Long categoryId,
                                  Long vendorId, PaymentMethod method) {
        ExpenseFacade.ExpenseInfo expense = expenseFacade.createExpense(
                schoolId, date, amount, categoryId, vendorId, method, null, null, null, null);
        expenseFacade.approveExpense(schoolId, expense.id());
        return expense.id();
    }

    @Test
    void summariesIncludeOnlyApprovedAndPaidWithinRangeAndResolveNames() {
        Long schoolId = 9301L;
        Long categoryA = expenseFacade.createCategory(schoolId, "دسته الف").id();
        Long categoryB = expenseFacade.createCategory(schoolId, "دسته ب").id();
        Long vendorA = expenseFacade.createVendor(schoolId, "فروشنده الف", null, null, null, null).id();

        LocalDate inRangeFrom = LocalDate.of(2026, 3, 1);
        LocalDate inRangeTo = LocalDate.of(2026, 3, 31);
        LocalDate insideDate = LocalDate.of(2026, 3, 15);
        LocalDate outsideDate = LocalDate.of(2026, 4, 15);

        // APPROVED در بازه، با طرف‌حساب - باید حساب شود
        long approvedInRange = approvedExpense(schoolId, insideDate, new BigDecimal("1000000"),
                categoryA, vendorA, PaymentMethod.CASH);

        // PAID در بازه، بدون طرف‌حساب - باید حساب شود و در گروه "بدون طرف حساب" بیفتد
        long paidExpenseId = approvedExpense(schoolId, insideDate, new BigDecimal("500000"),
                categoryB, null, PaymentMethod.BANK_TRANSFER);
        expenseFacade.payExpense(schoolId, paidExpenseId);

        // DRAFT در بازه - نباید حساب شود
        expenseFacade.createExpense(schoolId, insideDate, new BigDecimal("999999999"),
                categoryA, vendorA, PaymentMethod.CASH, null, null, null, null);

        // CANCELLED در بازه - نباید حساب شود
        ExpenseFacade.ExpenseInfo cancelled = expenseFacade.createExpense(
                schoolId, insideDate, new BigDecimal("777777777"), categoryA, null, PaymentMethod.CASH,
                null, null, null, null);
        expenseFacade.cancelExpense(schoolId, cancelled.id());

        // APPROVED ولی خارج از بازه - نباید حساب شود
        approvedExpense(schoolId, outsideDate, new BigDecimal("888888888"), categoryA, vendorA, PaymentMethod.CASH);

        List<CategorySummary> byCategory = expenseFacade.summaryByCategory(schoolId, inRangeFrom, inRangeTo);
        assertThat(byCategory).hasSize(2);
        assertThat(byCategory).anySatisfy(s -> {
            assertThat(s.categoryId()).isEqualTo(categoryA);
            assertThat(s.categoryName()).isEqualTo("دسته الف");
            assertThat(s.total()).isEqualByComparingTo("1000000");
            assertThat(s.count()).isEqualTo(1);
        });
        assertThat(byCategory).anySatisfy(s -> {
            assertThat(s.categoryId()).isEqualTo(categoryB);
            assertThat(s.total()).isEqualByComparingTo("500000");
        });

        List<VendorSummary> byVendor = expenseFacade.summaryByVendor(schoolId, inRangeFrom, inRangeTo);
        assertThat(byVendor).hasSize(2);
        assertThat(byVendor).anySatisfy(s -> {
            assertThat(s.vendorId()).isEqualTo(vendorA);
            assertThat(s.vendorName()).isEqualTo("فروشنده الف");
            assertThat(s.total()).isEqualByComparingTo("1000000");
        });
        assertThat(byVendor).anySatisfy(s -> {
            assertThat(s.vendorId()).isNull();
            assertThat(s.vendorName()).isEqualTo("بدون طرف حساب");
            assertThat(s.total()).isEqualByComparingTo("500000");
        });

        List<MethodSummary> byMethod = expenseFacade.summaryByMethod(schoolId, inRangeFrom, inRangeTo);
        assertThat(byMethod).hasSize(2);
        assertThat(byMethod).anySatisfy(s -> {
            assertThat(s.method()).isEqualTo(PaymentMethod.CASH);
            assertThat(s.total()).isEqualByComparingTo("1000000");
        });
        assertThat(byMethod).anySatisfy(s -> {
            assertThat(s.method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(s.total()).isEqualByComparingTo("500000");
        });

        assertThat(approvedInRange).isPositive();
    }

    @Test
    void financeSummaryComparesIncomeFromPaymentsAgainstExpenseTotal() {
        Long schoolId = 9302L;
        // recordPayment از Instant.now() استفاده می‌کند (بازه‌ی درآمد)، پس
        // بازه‌ی گزارش باید «امروز» را دربر بگیرد، نه یک ماه ثابتِ گذشته
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);
        LocalDate inRange = LocalDate.now();

        // درآمد: شهریه + فاکتور + پرداخت کامل
        financeFacade.setTuitionFee(schoolId, new BigDecimal("2000000"));
        financeFacade.issueTuitionInvoice(schoolId, 555L);
        Long invoiceId = financeFacade.listInvoices(schoolId).get(0).id();
        financeFacade.recordPayment(schoolId, invoiceId, new BigDecimal("2000000"), PaymentMethod.CASH, null);

        // هزینه: یک مورد تأییدشده در همان بازه
        Long categoryId = expenseFacade.createCategory(schoolId, "دسته سود و زیان").id();
        approvedExpense(schoolId, inRange, new BigDecimal("1200000"), categoryId, null, PaymentMethod.CASH);

        FinanceSummary summary = expenseFacade.financeSummary(schoolId, from, to);
        assertThat(summary.income()).isEqualByComparingTo("2000000");
        assertThat(summary.expense()).isEqualByComparingTo("1200000");
    }

    @Test
    void expenseStatusEnumHasExpectedValues() {
        assertThat(ExpenseStatus.values()).containsExactly(
                ExpenseStatus.DRAFT, ExpenseStatus.APPROVED, ExpenseStatus.PAID, ExpenseStatus.CANCELLED);
    }
}
