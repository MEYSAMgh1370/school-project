package com.example.roshdeandishe;

import com.example.roshdeandishe.web.dto.WardRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** وضعیت مالیِ یک فرزند فقط از شمارش فاکتورها می‌آید؛ دیرکرد بر بقیه مقدم است. */
class WardRowTest {

    private static WardRow ward(int invoices, int overdue, int open) {
        return new WardRow("آرین", "مدرسه", "پنجم", "الف", invoices, overdue, open);
    }

    @Test
    void noInvoicesIsItsOwnState() {
        WardRow w = ward(0, 0, 0);
        assertThat(w.hasNoInvoices()).isTrue();
        assertThat(w.isOverdue()).isFalse();
        assertThat(w.isOpen()).isFalse();
        assertThat(w.isSettled()).isFalse();
    }

    @Test
    void overdueBeatsOpenAndPaid() {
        WardRow w = ward(5, 1, 2);
        assertThat(w.isOverdue()).isTrue();
        assertThat(w.isOpen()).isFalse();
        assertThat(w.isSettled()).isFalse();
    }

    @Test
    void openWithoutOverdueIsOpen() {
        WardRow w = ward(3, 0, 2);
        assertThat(w.isOpen()).isTrue();
        assertThat(w.isOverdue()).isFalse();
        assertThat(w.isSettled()).isFalse();
    }

    @Test
    void onlyPaidInvoicesIsSettled() {
        WardRow w = ward(3, 0, 0);
        assertThat(w.isSettled()).isTrue();
        assertThat(w.isOpen()).isFalse();
    }

    @Test
    void classAndSectionAreOptional() {
        assertThat(new WardRow("آرین", "مدرسه", null, " ", 0, 0, 0).hasClass()).isFalse();
        assertThat(new WardRow("آرین", "مدرسه", "پنجم", null, 0, 0, 0).hasSection()).isFalse();
    }
}
