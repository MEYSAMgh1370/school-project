package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.shared.AcademicYearCodes;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.PaymentRecordedEvent;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
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
    private final SchoolFacade schoolFacade;
    private final int defaultDueDays;

    FinanceFacadeImpl(
            InvoiceRepository invoiceRepository,
            TuitionFeeRepository tuitionFeeRepository,
            PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher,
            IamFacade iamFacade,
            AcademicFacade academicFacade,
            SchoolFacade schoolFacade,
            @Value("${roshdeandishe.finance.default-due-days}") int defaultDueDays
    ) {
        this.invoiceRepository = invoiceRepository;
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
        this.schoolFacade = schoolFacade;
        this.defaultDueDays = defaultDueDays;
    }

    @Override
    @Transactional
    public void issueTuitionInvoice(Long schoolId, Long studentId) {
        // ثبت‌نام همیشه در «سالِ جاری» است (مدرسه‌ای که سالِ جاری ندارد: بدون سال، مثل پیش از فاز ۲)
        String year = resolveYear(schoolId, null);
        Optional<TuitionFeeEntity> fee = findFee(schoolId, year);
        if (fee.isEmpty()) {
            // تصمیم صریح (رجوع به doc متد در FinanceFacade): بدون شهریه‌ی
            // مصوب، چیزی برای صورت‌حساب کردن وجود ندارد؛ ثبت‌نام مسدود نمی‌شود
            log.warn("شهریه‌ای برای مدرسه‌ی {} (سال {}) تنظیم نشده - فاکتوری برای دانش‌آموز {} صادر نشد.",
                    schoolId, year, studentId);
            return;
        }
        issueInvoice(schoolId, studentId, year, fee.get().getAmount());
    }

    /** @return true اگر فاکتورِ تازه‌ای ساخته شد (نه اگر برای همین سال از قبل بود). */
    private boolean issueInvoice(Long schoolId, Long studentId, String year, BigDecimal amount) {
        Instant now = Instant.now();
        Instant dueDate = now.plus(Duration.ofDays(defaultDueDays));
        int inserted = invoiceRepository.insertTuitionInvoiceIfAbsent(schoolId, studentId, amount, now, dueDate, year);
        if (inserted == 0) {
            log.info("فاکتور شهریه‌ی مدرسه‌ی {} (سال {}) برای دانش‌آموز {} از قبل وجود دارد - تکراری صادر نشد.",
                    schoolId, year, studentId);
            return false;
        }

        eventPublisher.publishEvent(new InvoiceIssuedEvent(
                schoolId, studentId, amount, currentActorOrNull(), now
        ));
        return true;
    }

    @Override
    @Transactional
    public void setTuitionFee(Long schoolId, String academicYear, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidTuitionFee(
                    "مبلغ شهریه باید یک عدد مثبت باشد."
            ));
        }
        String year = resolveYear(schoolId, academicYear);

        // idempotent: ردیفِ موجودِ همان (مدرسه، سال) upsert می‌شود (ایندکس‌های یکتای جزئیِ V10)
        TuitionFeeEntity feeEntity = findFee(schoolId, year)
                .orElseGet(() -> new TuitionFeeEntity(schoolId, year, amount));

        feeEntity.setAmount(amount);
        tuitionFeeRepository.save(feeEntity);
    }

    // نسخه‌های بدونِ سال را خودِ پیاده‌سازی می‌دهد، نه default متدِ اینترفیس: default روی proxy به
    // متدِ دوآرگومانی «از داخل» صدا می‌زد و @Transactional دور زده می‌شد
    @Override
    @Transactional
    public void setTuitionFee(Long schoolId, BigDecimal amount) {
        setTuitionFee(schoolId, null, amount);
    }

    @Override
    public Optional<TuitionFeeInfo> getTuitionFee(Long schoolId) {
        return getTuitionFee(schoolId, null);
    }

    @Override
    @Transactional
    public int backfillInvoices(Long schoolId) {
        return backfillInvoices(schoolId, null);
    }

    @Override
    public Optional<TuitionFeeInfo> getTuitionFee(Long schoolId, String academicYear) {
        String year = resolveYear(schoolId, academicYear);
        return findFee(schoolId, year)
                .map(fee -> new TuitionFeeInfo(fee.getSchoolId(), fee.getAmount(), fee.getAcademicYear()));
    }

    @Override
    public List<InvoiceInfo> listInvoices(Long schoolId) {
        return invoiceRepository.findBySchoolIdOrderByIssuedAtDesc(schoolId).stream()
                .map(this::toInvoiceInfo)
                .toList();
    }

    @Override
    public List<InvoiceInfo> listInvoices(Long schoolId, String academicYear) {
        String year = resolveYear(schoolId, academicYear);
        if (year == null) {
            // مدرسه‌ای که سالِ جاری ندارد: همه، مثل پیش از فاز ۲
            return listInvoices(schoolId);
        }
        return invoiceRepository.findForYearOrUnattributed(schoolId, year).stream()
                .map(this::toInvoiceInfo)
                .toList();
    }

    @Override
    public List<InvoiceInfo> listInvoicesForStudents(Long schoolId, List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        // خانواده فقط سالِ جاری (و «بدون سال») را می‌بیند؛ مدرسه‌ای که سالِ جاری ندارد محدود نمی‌شود
        String currentYear = resolveYear(schoolId, null);
        List<InvoiceEntity> invoices = currentYear == null
                ? invoiceRepository.findBySchoolIdAndStudentIdInOrderByIssuedAtDesc(schoolId, studentIds)
                : invoiceRepository.findForStudentsInYearOrUnattributed(schoolId, studentIds, currentYear);
        return invoices.stream()
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
    public int backfillInvoices(Long schoolId, String academicYear) {
        String year = resolveYear(schoolId, academicYear);
        // برخلاف issueTuitionInvoice (که ثبت‌نام را مسدود نمی‌کند)، اینجا
        // نبود شهریه خطاست - ادمین صریحاً دکمه‌ی backfill را زده و سکوت
        // گمراه‌کننده بود
        TuitionFeeEntity fee = findFee(schoolId, year)
                .orElseThrow(() -> new FinanceException(new FinanceError.TuitionFeeNotSet(schoolId)));
        if (year != null && invoiceRepository.countBySchoolIdAndAcademicYearIsNull(schoolId) > 0) {
            throw new FinanceException(new FinanceError.UnattributedInvoicesExist(schoolId));
        }

        Set<Long> alreadyInvoiced = new HashSet<>(year == null
                ? invoiceRepository.findDistinctUnattributedStudentIdsBySchoolId(schoolId)
                : invoiceRepository.findDistinctStudentIdsBySchoolIdAndAcademicYear(schoolId, year));

        int issued = 0;
        for (AcademicFacade.StudentInfo student : academicFacade.listStudents(schoolId)) {
            Long studentId = student.id().value();
            if (alreadyInvoiced.contains(studentId)) {
                continue;
            }
            if (issueInvoice(schoolId, studentId, year, fee.getAmount())) {
                issued++;
            }
        }
        return issued;
    }

    @Override
    @Transactional
    public AttributionResult attributeToYear(Long schoolId, String academicYear) {
        String year = requireListedYear(academicYear);
        boolean legacyFee = tuitionFeeRepository.findBySchoolIdAndAcademicYearIsNull(schoolId).isPresent();
        if (legacyFee && tuitionFeeRepository.findBySchoolIdAndAcademicYear(schoolId, year).isPresent()) {
            throw new FinanceException(new FinanceError.AttributionConflict(year,
                    "سال «" + year + "» از قبل شهریه‌ی خودش را دارد؛ شهریه‌ی بدون سال با آن تداخل می‌کند."));
        }
        long conflicts = invoiceRepository.countAttributionConflicts(schoolId, year);
        if (conflicts > 0) {
            throw new FinanceException(new FinanceError.AttributionConflict(year,
                    conflicts + " دانش‌آموز هم فاکتورِ بدون سال و هم فاکتورِ سالِ «" + year
                            + "» دارند؛ نسبت دادن فاکتورِ تکراری می‌ساخت."));
        }
        int tuition = tuitionFeeRepository.attributeToYear(schoolId, year);
        int invoices = invoiceRepository.attributeToYear(schoolId, year);
        return new AttributionResult(tuition > 0, invoices);
    }

    @Override
    public UnattributedSummary unattributedSummary(Long schoolId) {
        return new UnattributedSummary(
                tuitionFeeRepository.findBySchoolIdAndAcademicYearIsNull(schoolId).isPresent(),
                invoiceRepository.countBySchoolIdAndAcademicYearIsNull(schoolId));
    }

    /**
     * سالِ هدفِ یک عملیات: سالِ خواسته‌شده (canonical و حتماً از فهرست)، وگرنه سالِ جاریِ مدرسه، وگرنه تهی
     * (مدرسه‌ای که سالِ جاری ندارد مثل پیش از فاز ۲ بدون سال کار می‌کند).
     */
    private String resolveYear(Long schoolId, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requireListedYear(requested);
        }
        // finance تا پیش از فاز ۲ schoolId را یک Long مات می‌دید و وجودِ مدرسه را بررسی نمی‌کرد (تست‌ها و
        // فراخوانی‌های داخلی با شناسه‌ی بدونِ ردیف مدرسه)؛ مدرسه‌ی ناشناخته مثل مدرسه‌ای بدونِ سالِ جاری
        // «بدون سال» کار می‌کند، نه خطا
        if (schoolFacade.findSchool(new SchoolId(schoolId)).isEmpty()) {
            return null;
        }
        return schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId)).map(AcademicYearInfo::code).orElse(null);
    }

    private String requireListedYear(String requested) {
        String canonical = AcademicYearCodes.canonical(requested);
        boolean listed = canonical != null && schoolFacade.listAcademicYears().stream()
                .anyMatch(year -> year.code().equals(canonical));
        if (!listed) {
            throw new FinanceException(new FinanceError.InvalidAcademicYear(requested));
        }
        return canonical;
    }

    private Optional<TuitionFeeEntity> findFee(Long schoolId, String year) {
        return year == null
                ? tuitionFeeRepository.findBySchoolIdAndAcademicYearIsNull(schoolId)
                : tuitionFeeRepository.findBySchoolIdAndAcademicYear(schoolId, year);
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
                deriveStatus(invoice),
                invoice.getAcademicYear()
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
