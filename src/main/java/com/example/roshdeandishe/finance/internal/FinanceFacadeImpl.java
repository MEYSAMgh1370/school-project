package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
class FinanceFacadeImpl implements FinanceFacade {

    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;

    FinanceFacadeImpl(ApplicationEventPublisher eventPublisher, IamFacade iamFacade) {
        this.eventPublisher = eventPublisher;
        this.iamFacade = iamFacade;
    }

    @Override
    public void issueTuitionInvoice(Long schoolId, Long studentId, BigDecimal amount) {
        // TODO: ایجاد رکورد فاکتور در جدول finance.invoices (در فاز بعدی توسعه)

        eventPublisher.publishEvent(new InvoiceIssuedEvent(
                schoolId, studentId, amount, currentActorOrNull(), Instant.now()
        ));
    }

    /**
     * این متد می‌تواند هم از یک درخواست HTTP واقعی (کاربر احراز هویت‌شده
     * موجود است) و هم از یک event listener داخلی مثل
     * StudentEnrolledEventListener (بدون کاربر احراز هویت‌شده) فراخوانی شود.
     * در حالت دوم actor را null می‌گذاریم به‌جای شکست دادن کل عملیات.
     */
    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
