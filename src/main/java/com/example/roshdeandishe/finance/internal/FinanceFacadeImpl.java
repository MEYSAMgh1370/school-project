package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
class FinanceFacadeImpl implements FinanceFacade {

    private static final Logger log = LoggerFactory.getLogger(FinanceFacadeImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;

    FinanceFacadeImpl(
            InvoiceRepository invoiceRepository,
            TuitionFeeRepository tuitionFeeRepository,
            ApplicationEventPublisher eventPublisher,
            IamFacade iamFacade
    ) {
        this.invoiceRepository = invoiceRepository;
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.eventPublisher = eventPublisher;
        this.iamFacade = iamFacade;
    }

    @Override
    @Transactional
    public void issueTuitionInvoice(Long schoolId, Long studentId) {
        Optional<TuitionFeeEntity> fee = tuitionFeeRepository.findBySchoolId(schoolId);
        if (fee.isEmpty()) {
            // تصمیم صریح (رجوع به doc متد در FinanceFacade): بدون شهریه‌ی
            // مصوب، چیزی برای صورت‌حساب کردن وجود ندارد؛ ثبت‌نام مسدود نمی‌شود
            log.warn("شهریه‌ای برای مدرسه‌ی {} تنظیم نشده - فاکتوری برای دانش‌آموز {} صادر نشد.",
                    schoolId, studentId);
            return;
        }

        BigDecimal amount = fee.get().getAmount();
        Instant now = Instant.now();
        invoiceRepository.save(new InvoiceEntity(schoolId, studentId, amount, now));

        eventPublisher.publishEvent(new InvoiceIssuedEvent(
                schoolId, studentId, amount, currentActorOrNull(), now
        ));
    }

    @Override
    @Transactional
    public void setTuitionFee(Long schoolId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidTuitionFee(
                    "مبلغ شهریه باید یک عدد مثبت باشد."
            ));
        }

        // idempotent: ردیف موجود upsert می‌شود (unique constraint روی school_id)
        TuitionFeeEntity feeEntity = tuitionFeeRepository.findBySchoolId(schoolId)
                .orElseGet(() -> new TuitionFeeEntity(schoolId, amount));

        feeEntity.setAmount(amount);
        tuitionFeeRepository.save(feeEntity);
    }

    @Override
    public Optional<TuitionFeeInfo> getTuitionFee(Long schoolId) {
        return tuitionFeeRepository.findBySchoolId(schoolId)
                .map(fee -> new TuitionFeeInfo(fee.getSchoolId(), fee.getAmount()));
    }

    @Override
    public List<InvoiceInfo> listInvoices(Long schoolId) {
        return invoiceRepository.findBySchoolIdOrderByIssuedAtDesc(schoolId).stream()
                .map(invoice -> new InvoiceInfo(
                        invoice.getId(),
                        invoice.getSchoolId(),
                        invoice.getStudentId(),
                        invoice.getAmount(),
                        invoice.getIssuedAt()
                ))
                .toList();
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
