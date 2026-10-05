package com.example.roshdeandishe.web.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * درصدِ پرداختِ شهریه‌ی یک مدرسه برای بخش «وضعیت شهریه» پیشخوانِ مدیر.
 * <p>
 * فقط یک نسبت است (جمعِ پرداخت‌شده ÷ جمعِ صورت‌حساب‌شده‌ی سالِ جاری)، نه مبلغ: واحد پولی هنوز
 * تصمیم‌نگرفته است (OpenSpec: changes/finance-monetary-unit.md) و نسبت به آن حساس نیست.
 *
 * @param invoiceCount تعدادِ فاکتورهای سالِ جاریِ مدرسه؛ صفر یعنی هنوز فاکتوری صادر نشده
 * @param percentPaid  عددِ صحیحِ ۰ تا ۱۰۰ (گردشده به پایین، تا ۹۹٫۹٪ هرگز «۱۰۰٪» نشان داده نشود)
 */
public record TuitionRow(Long schoolId, String schoolName, int invoiceCount, int percentPaid) {

    public static TuitionRow of(Long schoolId, String schoolName, int invoiceCount,
                                BigDecimal billed, BigDecimal paid) {
        if (invoiceCount == 0 || billed.signum() <= 0) {
            return new TuitionRow(schoolId, schoolName, invoiceCount, 0);
        }
        int percent = paid.multiply(BigDecimal.valueOf(100))
                .divide(billed, 0, RoundingMode.FLOOR).intValue();
        return new TuitionRow(schoolId, schoolName, invoiceCount, Math.max(0, Math.min(100, percent)));
    }

    public boolean hasInvoices() {
        return invoiceCount > 0;
    }
}
