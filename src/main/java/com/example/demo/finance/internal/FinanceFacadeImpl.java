package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.FinanceFacade;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
class FinanceFacadeImpl implements FinanceFacade {

    @Override
    public void issueTuitionInvoice(Long schoolId, Long studentId, BigDecimal amount) {
        // TODO: ایجاد رکورد فاکتور در جدول finance.invoices (در فاز بعدی توسعه)
    }
}
