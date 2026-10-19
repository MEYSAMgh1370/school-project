package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.shared.AcademicYearCodes;
import com.example.roshdeandishe.finance.DiscountEarnedEvent;
import com.example.roshdeandishe.finance.DiscountLapsedEvent;
import com.example.roshdeandishe.finance.DiscountStatus;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.InstallmentSnapshot;
import com.example.roshdeandishe.finance.InstallmentStatus;
import com.example.roshdeandishe.finance.InstallmentsChangeCause;
import com.example.roshdeandishe.finance.InstallmentsChangedEvent;
import com.example.roshdeandishe.finance.InstallmentsSettingChangedEvent;
import com.example.roshdeandishe.finance.InvoiceDiscountedEvent;
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
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class FinanceFacadeImpl implements FinanceFacade {

    private static final Logger log = LoggerFactory.getLogger(FinanceFacadeImpl.class);

    private static final int MAX_INSTALLMENTS = 60;
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    /** ترتیبِ طرح: (سررسید، seq، شناسه) و قسطِ بدون سررسید همیشه آخر. */
    private static final Comparator<InstallmentEntity> PLAN_ORDER = Comparator
            .comparing(InstallmentEntity::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparingInt(InstallmentEntity::getSeq)
            .thenComparing(InstallmentEntity::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    private final InvoiceRepository invoiceRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceDiscountRepository discountRepository;
    private final InstallmentRepository installmentRepository;
    private final FinanceAuditRepository financeAuditRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;
    private final SchoolFacade schoolFacade;
    private final int defaultDueDays;
    private final TransactionTemplate transactionTemplate;

    FinanceFacadeImpl(
            InvoiceRepository invoiceRepository,
            TuitionFeeRepository tuitionFeeRepository,
            PaymentRepository paymentRepository,
            InvoiceDiscountRepository discountRepository,
            InstallmentRepository installmentRepository,
            FinanceAuditRepository financeAuditRepository,
            ApplicationEventPublisher eventPublisher,
            IamFacade iamFacade,
            AcademicFacade academicFacade,
            SchoolFacade schoolFacade,
            PlatformTransactionManager transactionManager,
            @Value("${roshdeandishe.finance.default-due-days}") int defaultDueDays
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.invoiceRepository = invoiceRepository;
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.paymentRepository = paymentRepository;
        this.discountRepository = discountRepository;
        this.installmentRepository = installmentRepository;
        this.financeAuditRepository = financeAuditRepository;
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
        return toInvoiceInfos(schoolId, invoiceRepository.findBySchoolIdOrderByIssuedAtDesc(schoolId));
    }

    @Override
    public List<InvoiceInfo> listInvoices(Long schoolId, String academicYear) {
        return toInvoiceInfos(schoolId, findInvoices(schoolId, academicYear));
    }

    @Override
    public StudentFinancePage listStudentFinance(Long schoolId, StudentFinanceQuery query) {
        if (query.scope() == null || query.debt() == null) {
            throw new FinanceException(new FinanceError.InvalidFilter("بازه یا وضعیت بدهی نامعتبر است."));
        }
        if (query.page() < 0) {
            throw new FinanceException(new FinanceError.InvalidFilter("شماره‌ی صفحه نباید منفی باشد."));
        }
        if (query.size() < 1 || query.size() > 100) {
            throw new FinanceException(new FinanceError.InvalidFilter("اندازه‌ی صفحه باید بین ۱ تا ۱۰۰ باشد."));
        }
        // یک query برای فاکتورهای مدرسه؛ بقیه در حافظه (وضعیت/تخفیفِ در اثر همان toInvoiceInfos است)
        List<InvoiceInfo> invoices = toInvoiceInfos(schoolId, invoiceRepository.findBySchoolIdOrderByIssuedAtDesc(schoolId));
        String currentYear = resolveYear(schoolId, null);
        Map<Long, List<InvoiceInfo>> byStudent = invoices.stream()
                .collect(Collectors.groupingBy(InvoiceInfo::studentId));
        if (byStudent.isEmpty()) {
            return new StudentFinancePage(List.of(), 0, emptyCounts(), query.page(), query.size());
        }

        // دانش‌آموزِ فعال با یک query؛ ترک‌کرده/فارغ‌التحصیلِ دارای فاکتور تک‌به‌تک (فقط خودِ آن‌ها)
        Map<Long, AcademicFacade.StudentInfo> infos = new HashMap<>();
        academicFacade.listStudents(schoolId).forEach(s -> infos.put(s.id().value(), s));
        for (Long studentId : byStudent.keySet()) {
            if (!infos.containsKey(studentId)) {
                academicFacade.findStudentInfo(studentId)
                        .filter(s -> schoolId.equals(s.schoolId()))
                        .ifPresent(s -> infos.put(studentId, s));
            }
        }

        record Candidate(StudentFinanceRow row, String sortKey, Set<StudentScope> debtMatches) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, List<InvoiceInfo>> entry : byStudent.entrySet()) {
            AcademicFacade.StudentInfo info = infos.get(entry.getKey());
            String name = info == null ? null : userName(info.userId());
            String fullName = name == null || name.isBlank() ? UNKNOWN_NAME : name.strip();
            if (!StudentNameMatcher.matches(fullName, query.firstName(), query.lastName(), query.q())) {
                continue;
            }
            StudentFinanceRow row = toStudentRow(entry.getKey(), fullName, info, entry.getValue(), currentYear);
            // فیلتر بدهی فقط فاکتورهای همان بازه را می‌بیند؛ پس برای هر بازه جدا ارزیابی می‌شود
            Set<StudentScope> debtMatches = java.util.EnumSet.noneOf(StudentScope.class);
            for (StudentScope scope : StudentScope.values()) {
                List<InvoiceInfo> considered = entry.getValue().stream()
                        .filter(i -> invoiceInScope(i, scope, currentYear)).toList();
                if (matchesDebt(considered, query.debt())) {
                    debtMatches.add(scope);
                }
            }
            if (!debtMatches.isEmpty()) {
                candidates.add(new Candidate(row, StudentNameMatcher.normalise(fullName), debtMatches));
            }
        }

        Map<StudentScope, Long> counts = emptyCounts();
        for (StudentScope scope : StudentScope.values()) {
            counts.put(scope, candidates.stream()
                    .filter(c -> c.debtMatches().contains(scope) && inScope(c.row(), scope)).count());
        }
        List<StudentFinanceRow> scoped = candidates.stream()
                .filter(c -> c.debtMatches().contains(query.scope()) && inScope(c.row(), query.scope()))
                .sorted(Comparator.comparing(Candidate::sortKey).thenComparing(c -> c.row().studentId()))
                .map(Candidate::row)
                .toList();
        long from = (long) query.page() * query.size();
        List<StudentFinanceRow> items = from >= scoped.size() ? List.of()
                : scoped.subList((int) from, (int) Math.min(scoped.size(), from + query.size()));
        return new StudentFinancePage(items, scoped.size(), counts, query.page(), query.size());
    }

    private static final String UNKNOWN_NAME = "نامشخص";

    private static Map<StudentScope, Long> emptyCounts() {
        Map<StudentScope, Long> counts = new java.util.EnumMap<>(StudentScope.class);
        for (StudentScope scope : StudentScope.values()) {
            counts.put(scope, 0L);
        }
        return counts;
    }

    private StudentFinanceRow toStudentRow(Long studentId, String fullName, AcademicFacade.StudentInfo info,
                                           List<InvoiceInfo> invoices, String currentYear) {
        BigDecimal currentRemaining = BigDecimal.ZERO;
        BigDecimal previousDebt = BigDecimal.ZERO;
        InvoiceStatus currentStatus = null;
        boolean overdue = false;
        boolean hasCurrent = false;
        for (InvoiceInfo invoice : invoices) {
            BigDecimal remaining = remaining(invoice);
            // مدرسه‌ی بدون سالِ جاری: همه «جاری»اند؛ وگرنه سالِ دیگر یا تهی «قبل» است
            boolean current = currentYear == null || currentYear.equals(invoice.academicYear());
            if (current) {
                currentRemaining = currentRemaining.add(remaining);
                // بدترین وضعیتِ فاکتورهای جاری؛ ترتیب اعلام‌شده‌ی enum از بهترین به بدترین است:
                // PAID < PARTIAL < UNPAID < OVERDUE
                if (currentStatus == null || invoice.status().compareTo(currentStatus) > 0) {
                    currentStatus = invoice.status();
                }
                hasCurrent = true;
            } else {
                previousDebt = previousDebt.add(remaining);
            }
            overdue |= invoice.status() == InvoiceStatus.OVERDUE;
        }
        return new StudentFinanceRow(studentId, fullName, info == null ? null : info.status().name(), currentStatus,
                hasCurrent ? currentRemaining : BigDecimal.ZERO, previousDebt, currentRemaining.add(previousDebt),
                overdue);
    }

    /** مانده‌ی یک فاکتور = قابل پرداخت (مبلغ منهای تخفیفِ در اثر) منهای پرداخت‌شده، هرگز منفی. */
    private static BigDecimal remaining(InvoiceInfo invoice) {
        return invoice.amount().subtract(invoice.discountTotal()).subtract(invoice.amountPaid()).max(BigDecimal.ZERO);
    }

    /**
     * فاکتور در بازه هست؟ جاری = سالِ جاریِ مدرسه (یا همه، وقتی مدرسه سالِ جاری ندارد)؛ قبل = هر چیزِ دیگر
     * از جمله فاکتورِ بدون سال؛ همه = همه. همان قاعده‌ی toStudentRow.
     */
    private static boolean invoiceInScope(InvoiceInfo invoice, StudentScope scope, String currentYear) {
        boolean current = currentYear == null || currentYear.equals(invoice.academicYear());
        return switch (scope) {
            case CURRENT -> current;
            case PREVIOUS -> !current;
            case ALL -> true;
        };
    }

    /**
     * فقط فاکتورهای بازه‌ی انتخابی بررسی می‌شوند: OWING = مانده‌ی مثبت، OVERDUE = دست‌کم یک فاکتور دیرکرد،
     * SETTLED = دست‌کم یک فاکتور و هیچ‌کدام بدهکار نیست.
     */
    private static boolean matchesDebt(List<InvoiceInfo> considered, DebtFilter debt) {
        return switch (debt) {
            case ANY -> true;
            case OWING -> considered.stream().anyMatch(i -> remaining(i).signum() > 0);
            case OVERDUE -> considered.stream().anyMatch(i -> i.status() == InvoiceStatus.OVERDUE);
            case SETTLED -> !considered.isEmpty() && considered.stream().noneMatch(i -> remaining(i).signum() > 0);
        };
    }

    private static boolean inScope(StudentFinanceRow row, StudentScope scope) {
        boolean hasCurrent = row.currentStatus() != null;
        boolean debtor = row.previousDebt().signum() > 0;
        return switch (scope) {
            case CURRENT -> hasCurrent;
            case PREVIOUS -> debtor;
            case ALL -> hasCurrent || debtor;
        };
    }

    private String userName(Long userId) {
        return userId == null ? null
                : iamFacade.findUser(new IamFacade.UserId(userId)).map(IamFacade.UserProfile::fullName).orElse(null);
    }

    @Override
    public List<InvoiceInfo> listInvoicesOfStudent(Long schoolId, Long studentId) {
        // newest year first; «بدون سال» last, then newest issued
        return toInvoiceInfos(schoolId, invoiceRepository.findBySchoolIdAndStudentId(schoolId, studentId)).stream()
                .sorted(Comparator
                        .comparing(InvoiceInfo::academicYear, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                        .reversed()
                        .thenComparing(Comparator.comparing(InvoiceInfo::issuedAt).reversed())
                        .thenComparing(Comparator.comparing(InvoiceInfo::id).reversed()))
                .toList();
    }

    @Override
    public TuitionSummary tuitionSummary(Long schoolId, String academicYear) {
        // بدون بارگذاریِ اقساط: داشبورد فقط جمعِ صورت‌حساب و پرداخت را می‌خواهد
        List<InvoiceEntity> invoices = findInvoices(schoolId, academicYear);
        Map<Long, BigDecimal> discounts = effectiveDiscounts(schoolId, invoices, today(schoolId));
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (InvoiceEntity invoice : invoices) {
            billed = billed.add(invoice.getAmount().subtract(discounts.get(invoice.getId())));
            paid = paid.add(invoice.getAmountPaid());
        }
        return new TuitionSummary(invoices.size(), billed, paid);
    }

    /** فاکتورهای یک سال به‌علاوه‌ی «بدون سال»ها؛ مدرسه‌ای که سالِ جاری ندارد: همه، مثل پیش از فاز ۲. */
    private List<InvoiceEntity> findInvoices(Long schoolId, String academicYear) {
        String year = resolveYear(schoolId, academicYear);
        return year == null
                ? invoiceRepository.findBySchoolIdOrderByIssuedAtDesc(schoolId)
                : invoiceRepository.findForYearOrUnattributed(schoolId, year);
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
        return toInvoiceInfos(schoolId, invoices);
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
        // تخفیفِ مشروطِ مهلت‌گذشته اول برداشته می‌شود تا پرداخت «قابل پرداخت»ِ کهنه نبیند
        lapseExpiredDiscounts(invoice, "payment");

        BigDecimal remaining = payable(invoice).subtract(invoice.getAmountPaid());
        if (amount.compareTo(remaining) > 0) {
            throw new FinanceException(new FinanceError.PaymentExceedsBalance(invoiceId, remaining));
        }

        Instant now = Instant.now();
        Long actor = currentActorOrNull();
        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));
        invoiceRepository.save(invoice);
        // فاکتورِ دارای طرحِ اقساط: پرداخت به قدیمی‌ترین سررسیدِ دارای ظرفیت می‌رود (جمعِ پرداختیِ اقساط = amountPaid)
        allocatePayment(installmentRepository.findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(invoiceId), amount);
        PaymentEntity payment = paymentRepository.save(
                new PaymentEntity(invoiceId, schoolId, amount, method, note, actor, now));
        earnPendingDiscounts(invoice, now, "payment");

        eventPublisher.publishEvent(new PaymentRecordedEvent(
                schoolId, invoiceId, invoice.getStudentId(), amount, actor, now,
                method, note, payment.getId()
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

    // مثل setTuitionFee: نسخه‌ی کوتاه را خودِ پیاده‌سازی می‌دهد تا @Transactional دور زده نشود
    @Override
    @Transactional
    public DiscountInfo applyDiscount(Long schoolId, Long invoiceId, BigDecimal amount, String reason) {
        return applyDiscount(schoolId, invoiceId, amount, reason, null);
    }

    @Override
    @Transactional
    public DiscountInfo applyDiscount(Long schoolId, Long invoiceId, BigDecimal amount, String reason,
                                      LocalDate deadline) {
        if (amount == null || amount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidDiscount(
                    "مبلغ تخفیف باید یک عدد مثبت باشد."
            ));
        }
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 255) {
            throw new FinanceException(new FinanceError.InvalidDiscount(
                    "توضیح تخفیف نباید بیش از ۲۵۵ نویسه باشد."
            ));
        }
        BigDecimal discount = amount.setScale(2, RoundingMode.HALF_UP);
        if (discount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidDiscount(
                    "مبلغ تخفیف باید یک عدد مثبت باشد."
            ));
        }

        InvoiceEntity invoice = requireInvoiceForPayment(schoolId, invoiceId);
        lapseExpiredDiscounts(invoice, "discount");
        if (deadline != null && deadline.isBefore(today(schoolId))) {
            throw new FinanceException(new FinanceError.InvalidDiscount(
                    "مهلتِ تخفیف نباید پیش از امروز باشد."
            ));
        }

        // تخفیف نمی‌تواند قابل پرداخت را از آنچه پرداخت شده کمتر کند
        BigDecimal maxDiscount = payable(invoice).subtract(invoice.getAmountPaid());
        if (discount.compareTo(maxDiscount) > 0) {
            throw new FinanceException(new FinanceError.DiscountExceedsBalance(invoiceId, maxDiscount));
        }

        Instant now = Instant.now();
        Long actor = currentActorOrNull();
        invoice.setDiscountTotal(invoice.getDiscountTotal().add(discount));
        invoiceRepository.save(invoice);
        InvoiceDiscountEntity saved = discountRepository.save(
                new InvoiceDiscountEntity(schoolId, invoiceId, discount, trimmedReason, actor, now, deadline));
        resplitInstallmentsAfterDiscount(invoice, InstallmentsChangeCause.DISCOUNT_RESPLIT);

        eventPublisher.publishEvent(new InvoiceDiscountedEvent(
                schoolId, invoiceId, invoice.getStudentId(), discount, actor, now,
                saved.getId(), trimmedReason, deadline, saved.getStatus()
        ));
        // اگر همین تخفیف فاکتور را کاملاً تسویه کرد، شرطِ «تسویه‌ی کامل تا مهلت» همین الان برقرار است
        earnPendingDiscounts(invoice, now, "discount");

        return toDiscountInfo(saved);
    }

    @Override
    public Map<Long, PendingDiscountSummary> pendingDiscountsForInvoices(Long schoolId, Collection<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = today(schoolId);
        // مهلت‌گذشته‌هایی که هنوز جاروب نشده‌اند عملاً LAPSED‌اند؛ به خانواده نشان داده نمی‌شوند
        return discountRepository.findBySchoolIdAndInvoiceIdInAndStatus(schoolId, invoiceIds, DiscountStatus.PENDING)
                .stream()
                .filter(d -> d.getDeadline() != null && !d.getDeadline().isBefore(today))
                .collect(Collectors.groupingBy(InvoiceDiscountEntity::getInvoiceId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new PendingDiscountSummary(
                                entry.getValue().stream().map(InvoiceDiscountEntity::getAmount)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                                entry.getValue().stream().map(InvoiceDiscountEntity::getDeadline)
                                        .min(Comparator.naturalOrder()).orElseThrow())));
    }

    /**
     * جاروی تخفیف‌های مشروطِ مهلت‌گذشته (کارِ ساعتیِ ExpiredDiscountSweeper): هر فاکتور در تراکنشِ خودش و زیر
     * قفل؛ شکستِ یک فاکتور بقیه را متوقف نمی‌کند. Idempotent. عمداً در {@link FinanceFacade} نیست (کارِ نگهداریِ
     * داخلیِ همین ماژول است، نه API برای ماژول‌های دیگر)؛ public است تا proxyِ CGLIB آن را به targetِ اصلی بسپارد.
     *
     * @return تعداد تخفیف‌های LAPSEDشده
     */
    public int sweepExpiredDiscounts() {
        int lapsed = 0;
        for (Long invoiceId : discountRepository.findInvoiceIdsWithCandidateExpiredDiscounts(DiscountStatus.PENDING)) {
            try {
                Integer count = transactionTemplate.execute(status -> {
                    InvoiceEntity invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElse(null);
                    return invoice == null ? 0 : lapseExpiredDiscounts(invoice, "sweep");
                });
                lapsed += count == null ? 0 : count;
            } catch (RuntimeException e) {
                log.warn("برداشتنِ تخفیفِ مهلت‌گذشته‌ی فاکتور {} شکست خورد: {}", invoiceId,
                        e.getClass().getSimpleName());
            }
        }
        return lapsed;
    }

    @Override
    public List<DiscountInfo> listDiscounts(Long schoolId, Long invoiceId) {
        requireInvoice(schoolId, invoiceId);
        return discountRepository.findByInvoiceIdOrderByCreatedAtAscIdAsc(invoiceId).stream()
                .map(this::toDiscountInfo)
                .toList();
    }

    @Override
    public List<InstallmentInfo> listInstallments(Long schoolId, Long invoiceId) {
        requireInvoice(schoolId, invoiceId);
        LocalDate today = today(schoolId);
        return installmentRepository.findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(invoiceId).stream()
                .map(installment -> toInstallmentInfo(installment, today))
                .toList();
    }

    @Override
    public Map<Long, List<InstallmentInfo>> listInstallmentsForInvoices(Long schoolId, Collection<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = today(schoolId);
        return loadPlans(schoolId, invoiceIds).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(installment -> toInstallmentInfo(installment, today)).toList()));
    }

    @Override
    @Transactional
    public List<InstallmentInfo> generateInstallments(Long schoolId, Long invoiceId, int count,
                                                      LocalDate firstDueDate, int intervalMonths) {
        InvoiceEntity invoice = requireInvoiceForPayment(schoolId, invoiceId);
        lapseExpiredDiscounts(invoice, "installments");
        requireInstallmentsEnabled(schoolId);
        if (intervalMonths < 1 || intervalMonths > 12) {
            throw invalidInstallments("فاصله‌ی اقساط باید بین ۱ تا ۱۲ ماه باشد.");
        }

        List<InstallmentEntity> plan = installmentRepository.findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(invoiceId);
        List<InstallmentSnapshot> before = snapshots(plan);
        List<InstallmentEntity> kept = plan.stream().filter(InstallmentEntity::isPaid).toList();
        List<InstallmentEntity> oldUnpaid = plan.stream().filter(i -> !i.isPaid()).toList();
        BigDecimal remaining = payable(invoice).subtract(sumAmounts(kept));

        List<InstallmentEntity> fresh = new ArrayList<>();
        if (remaining.signum() <= 0) {
            if (count != 0) {
                throw invalidInstallments("مانده‌ای برای تقسیم نیست؛ تعداد اقساط باید صفر باشد.");
            }
        } else {
            if (count < 1 || count > MAX_INSTALLMENTS) {
                throw invalidInstallments("تعداد اقساط باید بین ۱ تا " + MAX_INSTALLMENTS + " باشد.");
            }
            if (firstDueDate == null) {
                throw invalidInstallments("تاریخ سررسید قسط اول الزامی است.");
            }
            List<BigDecimal> shares = split(remaining, count);
            if (shares.get(0).compareTo(MIN_AMOUNT) < 0) {
                throw invalidInstallments("مانده برای " + count + " قسط کافی نیست؛ هر قسط دست‌کم ۰٫۰۱ لازم دارد.");
            }
            for (int i = 0; i < count; i++) {
                LocalDate due = i == 0 ? firstDueDate : schoolFacade.addSolarMonths(firstDueDate, i * intervalMonths);
                fresh.add(new InstallmentEntity(schoolId, invoiceId, shares.get(i), due));
            }
        }

        installmentRepository.deleteAll(oldUnpaid);
        List<InstallmentEntity> all = finalizePlan(invoice, kept, fresh);
        return publishInstallmentsChanged(invoice, InstallmentsChangeCause.GENERATED, before, all);
    }

    @Override
    @Transactional
    public List<InstallmentInfo> updateInstallments(Long schoolId, Long invoiceId, List<InstallmentEdit> items) {
        InvoiceEntity invoice = requireInvoiceForPayment(schoolId, invoiceId);
        lapseExpiredDiscounts(invoice, "installments");
        requireInstallmentsEnabled(schoolId);

        List<InstallmentEntity> plan = installmentRepository.findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(invoiceId);
        if (plan.isEmpty()) {
            throw invalidInstallments("برای این فاکتور طرح اقساطی ثبت نشده است.");
        }
        if (items == null) {
            throw invalidInstallments("فهرست اقساط الزامی است.");
        }
        List<InstallmentSnapshot> before = snapshots(plan);
        List<InstallmentEntity> kept = plan.stream().filter(InstallmentEntity::isPaid).toList();
        List<InstallmentEntity> unpaid = plan.stream().filter(i -> !i.isPaid()).toList();
        Map<Long, InstallmentEntity> unpaidById = unpaid.stream()
                .collect(Collectors.toMap(InstallmentEntity::getId, i -> i));

        Set<Long> seen = new HashSet<>();
        Map<Long, BigDecimal> newAmounts = new HashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (InstallmentEdit item : items) {
            if (item == null || item.id() == null || item.amount() == null) {
                throw invalidInstallments("شناسه و مبلغِ هر قسط الزامی است.");
            }
            if (!seen.add(item.id())) {
                throw invalidInstallments("قسط " + item.id() + " بیش از یک بار آمده است.");
            }
            if (!unpaidById.containsKey(item.id())) {
                throw invalidInstallments("قسط " + item.id()
                        + " یا وجود ندارد یا پرداخت شده است و قابل ویرایش نیست.");
            }
            // سررسیدِ خالی فقط برای قسطی که همین حالا هم بدون سررسید است؛ تاریخِ موجود را نمی‌شود پاک کرد
            if (item.dueDate() == null && unpaidById.get(item.id()).getDueDate() != null) {
                throw invalidInstallments("سررسیدِ قسط " + item.id() + " را نمی‌توان خالی کرد.");
            }
            BigDecimal amount;
            try {
                amount = item.amount().setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw invalidInstallments("مبلغ قسط حداکثر دو رقم اعشار دارد.");
            }
            if (amount.signum() <= 0) {
                throw invalidInstallments("مبلغ هر قسط باید مثبت باشد.");
            }
            newAmounts.put(item.id(), amount);
            sum = sum.add(amount);
        }
        if (seen.size() != unpaid.size()) {
            throw invalidInstallments("فهرست باید همه‌ی اقساط پرداخت‌نشده‌ی فاکتور را شامل شود.");
        }
        BigDecimal expected = payable(invoice).subtract(sumAmounts(kept));
        if (sum.compareTo(expected) != 0) {
            throw new FinanceException(new FinanceError.InstallmentsSumMismatch(expected));
        }

        for (InstallmentEdit item : items) {
            InstallmentEntity installment = unpaidById.get(item.id());
            installment.setAmountPaid(BigDecimal.ZERO); // finalizePlan دوباره تخصیص می‌دهد
            installment.setAmount(newAmounts.get(item.id()));
            installment.setDueDate(item.dueDate());
        }
        List<InstallmentEntity> all = finalizePlan(invoice, kept, unpaid);
        return publishInstallmentsChanged(invoice, InstallmentsChangeCause.EDITED, before, all);
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
        // «دانش‌آموزانِ آن سال»: در مدرسه‌ی ردیاب‌شده ثبت‌نام‌های همان سال (academic-year.md فاز ۳)،
        // وگرنه همه‌ی دانش‌آموزانِ فعال مثل پیش از این
        List<AcademicFacade.StudentInfo> students = year == null
                ? academicFacade.listStudents(schoolId)
                : academicFacade.listStudentsEnrolledIn(schoolId, year);
        for (AcademicFacade.StudentInfo student : students) {
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
        try {
            int tuition = tuitionFeeRepository.attributeToYear(schoolId, year);
            int invoices = invoiceRepository.attributeToYear(schoolId, year);
            return new AttributionResult(tuition > 0, invoices);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // فاکتور/شهریه‌ی همان سال بعد از بررسیِ بالا و هم‌زمان ساخته شد (ثبت‌نام یا صدور): ایندکس‌های یکتای
            // جزئیِ V10 جلوی تکرار را می‌گیرند؛ تراکنش برمی‌گردد و ادمین پاسخِ تداخل می‌گیرد، نه 500
            throw new FinanceException(new FinanceError.AttributionConflict(year,
                    "هم‌زمان با نسبت دادن، شهریه یا فاکتورِ سالِ «" + year + "» ساخته شد؛ دوباره تلاش کنید."));
        }
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

    /** اقساطِ همه‌ی فاکتورهای فهرست با یک query بارگذاری می‌شود (بدون N+1). */
    private List<InvoiceInfo> toInvoiceInfos(Long schoolId, List<InvoiceEntity> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<Long, List<InstallmentEntity>> plans =
                loadPlans(schoolId, invoices.stream().map(InvoiceEntity::getId).toList());
        LocalDate today = today(schoolId);
        Map<Long, BigDecimal> discounts = effectiveDiscounts(schoolId, invoices, today);
        return invoices.stream()
                .map(invoice -> toInvoiceInfo(invoice, plans.getOrDefault(invoice.getId(), List.of()), today,
                        discounts.get(invoice.getId())))
                .toList();
    }

    /**
     * تخفیفِ «در اثر» هر فاکتور برای خواندن: تخفیف‌های PENDINGِ مهلت‌گذشته که جاروب/نوشتن هنوز LAPSEDشان نکرده
     * از discountTotal کم می‌شوند، تا مبلغ قابل پرداخت و وضعیتی که می‌خوانیم با آنچه اولین نوشتن می‌سازد یکی باشد
     * (پیامِ «تخفیف مشروط» هم با همین قاعده پنهان می‌شود). با یک query برای همه‌ی فاکتورهای فهرست؛ هیچ چیز نمی‌نویسد.
     */
    private Map<Long, BigDecimal> effectiveDiscounts(Long schoolId, List<InvoiceEntity> invoices, LocalDate today) {
        Map<Long, BigDecimal> expired = invoices.isEmpty() ? Map.of()
                : discountRepository.findBySchoolIdAndInvoiceIdInAndStatus(
                                schoolId, invoices.stream().map(InvoiceEntity::getId).toList(), DiscountStatus.PENDING)
                        .stream()
                        .filter(d -> d.getDeadline() != null && today.isAfter(d.getDeadline()))
                        .collect(Collectors.groupingBy(InvoiceDiscountEntity::getInvoiceId,
                                Collectors.reducing(BigDecimal.ZERO, InvoiceDiscountEntity::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> effective = new HashMap<>();
        for (InvoiceEntity invoice : invoices) {
            effective.put(invoice.getId(),
                    invoice.getDiscountTotal().subtract(expired.getOrDefault(invoice.getId(), BigDecimal.ZERO))
                            .max(BigDecimal.ZERO));
        }
        return effective;
    }

    private InvoiceInfo toInvoiceInfo(InvoiceEntity invoice, List<InstallmentEntity> plan, LocalDate today,
                                      BigDecimal discount) {
        return new InvoiceInfo(
                invoice.getId(),
                invoice.getSchoolId(),
                invoice.getStudentId(),
                invoice.getAmount(),
                invoice.getAmountPaid(),
                invoice.getIssuedAt(),
                invoice.getDueDate(),
                deriveStatus(invoice, plan, today, discount),
                invoice.getAcademicYear(),
                discount
        );
    }

    /** اقساطِ فاکتورها گروه‌بندی‌شده بر اساس فاکتور و مرتب‌شده به (سررسید، seq، شناسه)؛ بدون سررسید آخر. */
    private Map<Long, List<InstallmentEntity>> loadPlans(Long schoolId, Collection<Long> invoiceIds) {
        return installmentRepository.findBySchoolIdAndInvoiceIdIn(schoolId, invoiceIds).stream()
                .sorted(PLAN_ORDER)
                .collect(Collectors.groupingBy(InstallmentEntity::getInvoiceId));
    }

    /** «امروز» به وقتِ مدرسه؛ مدرسه‌ی ناشناخته (شناسه‌ی ساختگیِ تست) منطقه‌ی پیش‌فرض را می‌گیرد. */
    private LocalDate today(Long schoolId) {
        return LocalDate.now(zoneOf(schoolId));
    }

    private ZoneId zoneOf(Long schoolId) {
        return schoolFacade.findSchool(new SchoolId(schoolId)).map(SchoolInfo::zoneId).orElse(SchoolInfo.DEFAULT_ZONE);
    }

    private InstallmentInfo toInstallmentInfo(InstallmentEntity installment, LocalDate today) {
        return new InstallmentInfo(
                installment.getId(),
                installment.getInvoiceId(),
                installment.getSeq(),
                installment.getAmount(),
                installment.getAmountPaid(),
                installment.getDueDate(),
                deriveInstallmentStatus(installment, today)
        );
    }

    /** مثل وضعیتِ فاکتور هرگز persist نمی‌شود؛ رجوع به doc خود InstallmentStatus. */
    private static InstallmentStatus deriveInstallmentStatus(InstallmentEntity installment, LocalDate today) {
        if (installment.isPaid()) {
            return InstallmentStatus.PAID;
        }
        // قسطِ بدون سررسید هرگز دیرکرد نمی‌شود
        if (installment.getDueDate() != null && installment.getDueDate().isBefore(today)) {
            return InstallmentStatus.OVERDUE;
        }
        return installment.getAmountPaid().signum() > 0 ? InstallmentStatus.PARTIAL : InstallmentStatus.UNPAID;
    }

    private void requireInstallmentsEnabled(Long schoolId) {
        SchoolId id = new SchoolId(schoolId);
        // مدرسه‌ی ناشناخته مثل مدرسه‌ای با اقساطِ خاموش است (finance شناسه را مات می‌بیند)، نه خطای مدرسه
        if (schoolFacade.findSchool(id).isEmpty() || !schoolFacade.isInstallmentsEnabled(id)) {
            throw new FinanceException(new FinanceError.InstallmentsNotEnabled(schoolId));
        }
    }

    private static FinanceException invalidInstallments(String reason) {
        return new FinanceException(new FinanceError.InvalidInstallments(reason));
    }

    private static BigDecimal sumAmounts(List<InstallmentEntity> installments) {
        return installments.stream().map(InstallmentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** تقسیمِ مساوی در مقیاس ۲: هر سهم کف می‌شود و قسطِ آخر باقی‌مانده‌ی گرد کردن را می‌گیرد. */
    private static List<BigDecimal> split(BigDecimal total, int count) {
        BigDecimal share = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < count - 1; i++) {
            shares.add(share);
        }
        shares.add(total.subtract(share.multiply(BigDecimal.valueOf(count - 1L))).setScale(2, RoundingMode.HALF_UP));
        return shares;
    }

    /** پرداخت را به ترتیبِ (سررسید، seq) روی اقساطِ دارای ظرفیت پر می‌کند؛ بدون قسط هیچ کاری نمی‌کند. */
    private void allocatePayment(List<InstallmentEntity> plan, BigDecimal amount) {
        BigDecimal rest = amount.setScale(2, RoundingMode.HALF_UP);
        // اول اقساطِ دارای تاریخ، سپس بدون سررسید
        for (InstallmentEntity installment : plan.stream().sorted(PLAN_ORDER).toList()) {
            if (rest.signum() <= 0) {
                break;
            }
            BigDecimal capacity = installment.getAmount().subtract(installment.getAmountPaid());
            if (capacity.signum() <= 0) {
                continue;
            }
            BigDecimal take = capacity.min(rest);
            installment.setAmountPaid(installment.getAmountPaid().add(take));
            rest = rest.subtract(take);
        }
        installmentRepository.saveAll(plan);
    }

    /**
     * پایانِ هر تغییرِ ساختاری: اقساطِ تازه ذخیره می‌شود (برای شناسه)، seq به ترتیبِ (سررسید، شناسه) از ۱ شماره
     * می‌خورد، پرداختیِ فاکتور که به اقساطِ پرداخت‌شده‌ی ثابت نرسیده (pool) دوباره به ترتیبِ (سررسید، seq) روی
     * اقساطِ پرداخت‌نشده پخش می‌شود (جمعِ پرداختیِ اقساط = amountPaid)، و سررسیدِ فاکتور به روزِ پس از آخرین
     * قسطِ دارای تاریخ (به وقتِ مدرسه) می‌رود تا منطقِ قدیمیِ «now > dueDate» درست بماند.
     *
     * @return همه‌ی اقساطِ طرح مرتب
     */
    private List<InstallmentEntity> finalizePlan(InvoiceEntity invoice, List<InstallmentEntity> kept,
                                                 List<InstallmentEntity> unpaid) {
        installmentRepository.saveAll(unpaid);
        List<InstallmentEntity> all = new ArrayList<>(kept);
        all.addAll(unpaid);
        all.sort(Comparator.comparing(InstallmentEntity::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(InstallmentEntity::getId));

        BigDecimal pool = invoice.getAmountPaid().subtract(sumAmounts(kept)).max(BigDecimal.ZERO);
        int seq = 1;
        for (InstallmentEntity installment : all) {
            installment.setSeq(seq++);
            if (!kept.contains(installment)) {
                BigDecimal take = pool.min(installment.getAmount());
                installment.setAmountPaid(take);
                pool = pool.subtract(take);
            }
        }
        installmentRepository.saveAll(all);

        // سررسیدِ فاکتور از آخرین قسطِ «دارای تاریخ» می‌آید؛ اگر هیچ قسطی تاریخ ندارد دست‌نخورده می‌ماند
        all.stream().map(InstallmentEntity::getDueDate).filter(java.util.Objects::nonNull)
                .reduce((first, second) -> second).ifPresent(last -> {
                    invoice.setDueDate(last.plusDays(1).atStartOfDay(zoneOf(invoice.getSchoolId())).toInstant());
                    invoiceRepository.save(invoice);
                });
        return all;
    }

    /**
     * تخفیف روی فاکتورِ دارای طرح: اقساطِ پرداخت‌نشده روی همان سررسیدها دوباره مساوی تقسیم می‌شوند و تعدادشان
     * می‌ماند (اگر مانده کمتر از count*۰٫۰۱ باشد کم می‌شود؛ اگر مانده‌ای نماند حذف می‌شوند). به تنظیمِ اقساط
     * وابسته نیست - خاموش‌کردنِ تنظیم طرحِ موجود را متوقف نمی‌کند.
     */
    private void resplitInstallmentsAfterDiscount(InvoiceEntity invoice, InstallmentsChangeCause cause) {
        List<InstallmentEntity> plan =
                installmentRepository.findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(invoice.getId());
        if (plan.isEmpty()) {
            return;
        }
        List<InstallmentSnapshot> before = snapshots(plan);
        List<InstallmentEntity> kept = plan.stream().filter(InstallmentEntity::isPaid).toList();
        List<InstallmentEntity> unpaid = plan.stream().filter(i -> !i.isPaid()).toList();
        BigDecimal remaining = payable(invoice).subtract(sumAmounts(kept));

        List<InstallmentEntity> survivors = new ArrayList<>();
        if (remaining.signum() > 0 && unpaid.isEmpty()) {
            // مانده بالا رفته (برداشته شدنِ تخفیف) ولی قسطِ پرداخت‌نشده‌ای نمانده: یک قسطِ «بدون سررسید»؛
            // سیستم تاریخ نمی‌سازد و ادمین بعداً تاریخ می‌زند
            survivors.add(new InstallmentEntity(invoice.getSchoolId(), invoice.getId(),
                    remaining.setScale(2, RoundingMode.HALF_UP), null));
        } else if (remaining.signum() > 0) {
            long fits = remaining.movePointRight(2).longValue();
            int count = (int) Math.min(unpaid.size(), fits);
            survivors.addAll(unpaid.subList(0, count));
            List<BigDecimal> shares = split(remaining, count);
            for (int i = 0; i < count; i++) {
                InstallmentEntity installment = survivors.get(i);
                installment.setAmountPaid(BigDecimal.ZERO); // finalizePlan دوباره تخصیص می‌دهد
                installment.setAmount(shares.get(i));
            }
        }
        installmentRepository.deleteAll(unpaid.stream().filter(i -> !survivors.contains(i)).toList());
        List<InstallmentEntity> all = finalizePlan(invoice, kept, survivors);
        publishInstallmentsChanged(invoice, cause, before, all);
    }

    /**
     * تخفیف‌های PENDING که روزِ مهلتشان (به وقتِ مدرسه) کاملاً گذشته LAPSED می‌شوند: discountTotal کم می‌شود،
     * اقساطِ پرداخت‌نشده با همان منطقِ تخفیف (که مانده‌ی بالا رفته را هم می‌پذیرد) دوباره تقسیم می‌شوند و
     * DiscountLapsedEvent می‌رود. باید داخل تراکنشِ فراخوان و پس از قفلِ فاکتور صدا زده شود؛ idempotent.
     *
     * @return تعداد تخفیف‌های LAPSEDشده
     */
    private int lapseExpiredDiscounts(InvoiceEntity invoice, String trigger) {
        List<InvoiceDiscountEntity> pending = discountRepository.findByInvoiceIdAndStatusOrderByCreatedAtAscIdAsc(
                invoice.getId(), DiscountStatus.PENDING);
        if (pending.isEmpty()) {
            return 0;
        }
        LocalDate today = today(invoice.getSchoolId());
        List<InvoiceDiscountEntity> expired = pending.stream()
                .filter(d -> d.getDeadline() != null && today.isAfter(d.getDeadline()))
                .toList();
        if (expired.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        BigDecimal lapsedTotal = BigDecimal.ZERO;
        for (InvoiceDiscountEntity discount : expired) {
            discount.setStatus(DiscountStatus.LAPSED);
            discount.setResolvedAt(now);
            lapsedTotal = lapsedTotal.add(discount.getAmount());
        }
        discountRepository.saveAll(expired);
        invoice.setDiscountTotal(invoice.getDiscountTotal().subtract(lapsedTotal));
        invoiceRepository.save(invoice);
        resplitInstallmentsAfterDiscount(invoice, InstallmentsChangeCause.DISCOUNT_LAPSE_RESPLIT);

        for (InvoiceDiscountEntity discount : expired) {
            eventPublisher.publishEvent(new DiscountLapsedEvent(
                    invoice.getSchoolId(), invoice.getId(), invoice.getStudentId(),
                    discount.getId(), discount.getAmount(), now, discount.getDeadline(), trigger
            ));
        }
        return expired.size();
    }

    /** فاکتور کاملاً تسویه شد: تخفیف‌های PENDINGِ مهلت‌نگذشته EARNED (دائمی) می‌شوند. */
    private void earnPendingDiscounts(InvoiceEntity invoice, Instant now, String trigger) {
        if (invoice.getAmountPaid().compareTo(payable(invoice)) < 0) {
            return;
        }
        List<InvoiceDiscountEntity> pending = discountRepository.findByInvoiceIdAndStatusOrderByCreatedAtAscIdAsc(
                invoice.getId(), DiscountStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        LocalDate today = today(invoice.getSchoolId());
        List<InvoiceDiscountEntity> earned = pending.stream()
                .filter(d -> d.getDeadline() != null && !today.isAfter(d.getDeadline()))
                .toList();
        for (InvoiceDiscountEntity discount : earned) {
            discount.setStatus(DiscountStatus.EARNED);
            discount.setResolvedAt(now);
        }
        discountRepository.saveAll(earned);
        for (InvoiceDiscountEntity discount : earned) {
            eventPublisher.publishEvent(new DiscountEarnedEvent(
                    invoice.getSchoolId(), invoice.getId(), invoice.getStudentId(),
                    discount.getId(), discount.getAmount(), now, discount.getDeadline(), trigger
            ));
        }
    }

    /**
     * تصویرِ طرح پیش از هر تغییرِ موجودیت‌ها (فراخوان باید قبل از دست‌زدن بگیرد). مبلغ‌ها در مقیاس ۲ تا
     * مقایسه‌ی «بدون تغییر» به مقیاسِ ذخیره وابسته نباشد.
     */
    private static List<InstallmentSnapshot> snapshots(List<InstallmentEntity> plan) {
        return plan.stream().sorted(PLAN_ORDER)
                .map(i -> new InstallmentSnapshot(
                        i.getAmount().setScale(2, RoundingMode.HALF_UP),
                        i.getAmountPaid().setScale(2, RoundingMode.HALF_UP),
                        i.getDueDate()))
                .toList();
    }

    /** رویدادِ audit فقط وقتی منتشر می‌شود که طرح واقعاً عوض شده باشد (before != after). */
    private List<InstallmentInfo> publishInstallmentsChanged(InvoiceEntity invoice, InstallmentsChangeCause cause,
                                                             List<InstallmentSnapshot> before,
                                                             List<InstallmentEntity> all) {
        List<InstallmentSnapshot> after = snapshots(all);
        if (!before.equals(after)) {
            eventPublisher.publishEvent(new InstallmentsChangedEvent(
                    invoice.getSchoolId(), invoice.getId(), invoice.getStudentId(), cause, before, after,
                    currentActorOrNull(), Instant.now()
            ));
        }
        LocalDate today = today(invoice.getSchoolId());
        return all.stream().map(installment -> toInstallmentInfo(installment, today)).toList();
    }

    @Override
    @Transactional
    public void setInstallmentsEnabled(Long schoolId, boolean enabled) {
        SchoolId id = new SchoolId(schoolId);
        boolean previous = schoolFacade.isInstallmentsEnabled(id);
        schoolFacade.setInstallmentsEnabled(id, enabled);
        if (previous != enabled) {
            eventPublisher.publishEvent(new InstallmentsSettingChangedEvent(
                    schoolId, previous, enabled, currentActorOrNull(), Instant.now()
            ));
        }
    }

    /** مبلغ قابل پرداخت = مبلغ فاکتور منهای تخفیف‌ها. */
    private static BigDecimal payable(InvoiceEntity invoice) {
        return invoice.getAmount().subtract(invoice.getDiscountTotal());
    }

    private DiscountInfo toDiscountInfo(InvoiceDiscountEntity discount) {
        return new DiscountInfo(
                discount.getId(),
                discount.getInvoiceId(),
                discount.getAmount(),
                discount.getReason(),
                discount.getCreatedByUserId(),
                discount.getCreatedAt(),
                discount.getDeadline(),
                discount.getStatus(),
                discount.getResolvedAt()
        );
    }

    /**
     * وضعیت هرگز persist نمی‌شود (رجوع به doc خود InvoiceStatus):
     * پرداخت کامل همیشه PAID است؛ در غیر این صورت گذر از سررسید (اگر
     * سررسیدی باشد - ردیف‌های قدیمی dev ندارند) یعنی OVERDUE، وگرنه
     * PARTIAL/UNPAID بر اساس وجود پرداخت جزئی.
     */
    private InvoiceStatus deriveStatus(InvoiceEntity invoice, List<InstallmentEntity> plan, LocalDate today,
                                       BigDecimal discount) {
        if (invoice.getAmountPaid().compareTo(invoice.getAmount().subtract(discount)) >= 0) {
            return InvoiceStatus.PAID;
        }
        if (!plan.isEmpty()) {
            // فاکتورِ دارای طرح: OVERDUE یعنی دست‌کم یک قسطِ پرداخت‌نشده سررسیدش گذشته
            if (plan.stream().anyMatch(i -> deriveInstallmentStatus(i, today) == InstallmentStatus.OVERDUE)) {
                return InvoiceStatus.OVERDUE;
            }
            return invoice.getAmountPaid().signum() > 0 ? InvoiceStatus.PARTIAL : InvoiceStatus.UNPAID;
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
    private static final com.fasterxml.jackson.databind.ObjectMapper AUDIT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Instant AUDIT_MIN = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant AUDIT_MAX = Instant.parse("9999-01-01T00:00:00Z");

    @Override
    @Transactional(readOnly = true)
    public AuditPage listAuditTrail(Long schoolId, AuditQuery query) {
        if (query.page() < 0) {
            throw new FinanceException(new FinanceError.InvalidAuditFilter("شماره‌ی صفحه نباید منفی باشد."));
        }
        if (query.size() < 1 || query.size() > 100) {
            throw new FinanceException(new FinanceError.InvalidAuditFilter("اندازه‌ی صفحه باید بین ۱ تا ۱۰۰ باشد."));
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new FinanceException(new FinanceError.InvalidAuditFilter("تاریخِ شروع نباید بعد از تاریخِ پایان باشد."));
        }
        ZoneId zone = zoneOf(schoolId);
        Instant from = query.from() == null ? AUDIT_MIN : query.from().atStartOfDay(zone).toInstant();
        Instant toExclusive = query.to() == null ? AUDIT_MAX : query.to().plusDays(1).atStartOfDay(zone).toInstant();
        String action = query.action() == null || query.action().isBlank() ? null : query.action().trim();

        org.springframework.data.domain.Page<FinanceAuditEntity> page = financeAuditRepository.search(
                schoolId, query.invoiceId(), query.studentId(), action, from, toExclusive,
                org.springframework.data.domain.PageRequest.of(query.page(), query.size()));

        Map<Long, String> userNames = new HashMap<>();
        Map<Long, String> studentNames = new HashMap<>();
        resolveAuditNames(schoolId, page.getContent(), userNames, studentNames);

        List<AuditEntry> items = page.getContent().stream().map(row -> new AuditEntry(
                row.getId(), row.getOccurredAt(), row.getAction(),
                row.getActorUserId(), row.getActorUserId() == null ? null : userNames.get(row.getActorUserId()),
                row.getStudentId(), row.getStudentId() == null ? null : studentNames.get(row.getStudentId()),
                row.getInvoiceId(), row.getExpenseId(), row.getAmount(), parseAuditDetails(row.getDetails())
        )).toList();
        return new AuditPage(query.page(), query.size(), page.getTotalElements(), items);
    }

    /**
     * نامِ کاربرها با یک خواندنِ عضویت‌های مدرسه و نامِ دانش‌آموزها با یک خواندنِ فهرستِ دانش‌آموزان؛ فقط کاربری که
     * عضوِ این مدرسه نیست (مثلاً ادمینِ پلتفرم) با findUser جداگانه (فقط شناسه‌های یکتای همین صفحه) پر می‌شود.
     */
    private void resolveAuditNames(Long schoolId, List<FinanceAuditEntity> rows, Map<Long, String> userNames,
                                   Map<Long, String> studentNames) {
        Set<Long> actorIds = rows.stream().map(FinanceAuditEntity::getActorUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> studentIds = rows.stream().map(FinanceAuditEntity::getStudentId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (actorIds.isEmpty() && studentIds.isEmpty()) {
            return;
        }
        Map<Long, String> memberNames = new HashMap<>();
        for (IamFacade.MembershipInfo membership : iamFacade.findMembershipsBySchool(schoolId)) {
            memberNames.putIfAbsent(membership.userId().value(), membership.fullName());
        }
        for (Long actorId : actorIds) {
            userNames.put(actorId, nameOfUser(actorId, memberNames));
        }
        if (!studentIds.isEmpty()) {
            for (AcademicFacade.StudentInfo student : academicFacade.listStudents(schoolId)) {
                if (studentIds.contains(student.id().value())) {
                    studentNames.put(student.id().value(), nameOfUser(student.userId(), memberNames));
                }
            }
        }
    }

    private String nameOfUser(Long userId, Map<Long, String> memberNames) {
        String name = memberNames.get(userId);
        if (name != null) {
            return name;
        }
        return iamFacade.findUser(new IamFacade.UserId(userId)).map(IamFacade.UserProfile::fullName).orElse(null);
    }

    private static Map<String, Object> parseAuditDetails(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = AUDIT_JSON.readTree(text);
            return node != null && node.isObject()
                    ? AUDIT_JSON.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { })
                    : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
