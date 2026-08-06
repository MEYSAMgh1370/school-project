package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.PaymentRecordedEvent;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
class FinanceFacadeImpl implements FinanceFacade {

    private static final Logger log = LoggerFactory.getLogger(FinanceFacadeImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;
    private final int defaultDueDays;

    FinanceFacadeImpl(
            InvoiceRepository invoiceRepository,
            TuitionFeeRepository tuitionFeeRepository,
            PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher,
            IamFacade iamFacade,
            AcademicFacade academicFacade,
            @Value("${roshdeandishe.finance.default-due-days}") int defaultDueDays
    ) {
        this.invoiceRepository = invoiceRepository;
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
        this.defaultDueDays = defaultDueDays;
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
        Instant dueDate = now.plus(Duration.ofDays(defaultDueDays));
        int inserted = invoiceRepository.insertTuitionInvoiceIfAbsent(schoolId, studentId, amount, now, dueDate);
        if (inserted == 0) {
            log.info("فاکتور شهریه‌ی مدرسه‌ی {} برای دانش‌آموز {} از قبل وجود دارد - تکراری صادر نشد.",
                    schoolId, studentId);
            return;
        }

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
                .map(this::toInvoiceInfo)
                .toList();
    }

    @Override
    public List<InvoiceInfo> listInvoicesForStudents(Long schoolId, List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return invoiceRepository.findBySchoolIdAndStudentIdInOrderByIssuedAtDesc(schoolId, studentIds).stream()
                .map(this::toInvoiceInfo)
                .toList();
    }

    @Override
    @Transactional
    public PaymentInfo recordPayment(Long schoolId, Long invoiceId, BigDecimal amount,
                                     PaymentMethod method, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidPaymentAmount(
                    "مبلغ پرداخت باید یک عدد مثبت باشد."
            ));
        }
        if (method == null) {
            throw new FinanceException(new FinanceError.InvalidPaymentAmount(
                    "روش پرداخت الزامی است."
            ));
        }

        InvoiceEntity invoice = requireInvoiceForPayment(schoolId, invoiceId);

        BigDecimal remaining = invoice.getAmount().subtract(invoice.getAmountPaid());
        if (amount.compareTo(remaining) > 0) {
            throw new FinanceException(new FinanceError.PaymentExceedsBalance(invoiceId, remaining));
        }

        Instant now = Instant.now();
        Long actor = currentActorOrNull();
        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));
        invoiceRepository.save(invoice);
        PaymentEntity payment = paymentRepository.save(
                new PaymentEntity(invoiceId, schoolId, amount, method, note, actor, now));

        eventPublisher.publishEvent(new PaymentRecordedEvent(
                schoolId, invoiceId, invoice.getStudentId(), amount, actor, now
        ));

        return toPaymentInfo(payment);
    }

    @Override
    public List<PaymentInfo> listPayments(Long schoolId, Long invoiceId) {
        requireInvoice(schoolId, invoiceId);
        return paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoiceId).stream()
                .map(this::toPaymentInfo)
                .toList();
    }

    @Override
    @Transactional
    public int backfillInvoices(Long schoolId) {
        // برخلاف issueTuitionInvoice (که ثبت‌نام را مسدود نمی‌کند)، اینجا
        // نبود شهریه خطاست - ادمین صریحاً دکمه‌ی backfill را زده و سکوت
        // گمراه‌کننده بود
        tuitionFeeRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new FinanceException(new FinanceError.TuitionFeeNotSet(schoolId)));

        Set<Long> alreadyInvoiced = new HashSet<>(invoiceRepository.findDistinctStudentIdsBySchoolId(schoolId));

        int issued = 0;
        for (AcademicFacade.StudentInfo student : academicFacade.listStudents(schoolId)) {
            Long studentId = student.id().value();
            if (alreadyInvoiced.contains(studentId)) {
                continue;
            }
            issueTuitionInvoice(schoolId, studentId);
            issued++;
        }
        return issued;
    }

    private InvoiceEntity requireInvoice(Long schoolId, Long invoiceId) {
        // فاکتور مدرسه‌ی دیگر هم NotFound است، نه Forbidden - وجودش را لو نمی‌دهیم
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> invoice.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new FinanceException(new FinanceError.InvoiceNotFound(invoiceId)));
    }

    private InvoiceEntity requireInvoiceForPayment(Long schoolId, Long invoiceId) {
        return invoiceRepository.findByIdAndSchoolIdForUpdate(schoolId, invoiceId)
                .orElseThrow(() -> new FinanceException(new FinanceError.InvoiceNotFound(invoiceId)));
    }

    private InvoiceInfo toInvoiceInfo(InvoiceEntity invoice) {
        return new InvoiceInfo(
                invoice.getId(),
                invoice.getSchoolId(),
                invoice.getStudentId(),
                invoice.getAmount(),
                invoice.getAmountPaid(),
                invoice.getIssuedAt(),
                invoice.getDueDate(),
                deriveStatus(invoice)
        );
    }

    /**
     * وضعیت هرگز persist نمی‌شود (رجوع به doc خود InvoiceStatus):
     * پرداخت کامل همیشه PAID است؛ در غیر این صورت گذر از سررسید (اگر
     * سررسیدی باشد - ردیف‌های قدیمی dev ندارند) یعنی OVERDUE، وگرنه
     * PARTIAL/UNPAID بر اساس وجود پرداخت جزئی.
     */
    private InvoiceStatus deriveStatus(InvoiceEntity invoice) {
        if (invoice.getAmountPaid().compareTo(invoice.getAmount()) >= 0) {
            return InvoiceStatus.PAID;
        }
        if (invoice.getDueDate() != null && Instant.now().isAfter(invoice.getDueDate())) {
            return InvoiceStatus.OVERDUE;
        }
        return invoice.getAmountPaid().signum() > 0 ? InvoiceStatus.PARTIAL : InvoiceStatus.UNPAID;
    }

    private PaymentInfo toPaymentInfo(PaymentEntity payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getNote(),
                payment.getRecordedByUserId(),
                payment.getPaidAt()
        );
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
