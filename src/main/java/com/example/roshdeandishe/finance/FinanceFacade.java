package com.example.roshdeandishe.finance;

import java.math.BigDecimal;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Finance.
 */
public interface FinanceFacade {

    void issueTuitionInvoice(Long schoolId, Long studentId, BigDecimal amount);
}
