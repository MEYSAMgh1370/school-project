package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentResponse(Long id, int seq, BigDecimal amount, BigDecimal amountPaid,
                                  LocalDate dueDate, InstallmentStatus status) {
    public static InstallmentResponse from(InstallmentInfo info) {
        return new InstallmentResponse(info.id(), info.seq(), info.amount(), info.amountPaid(),
                info.dueDate(), info.status());
    }
}
