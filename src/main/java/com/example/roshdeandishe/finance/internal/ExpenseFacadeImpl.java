package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.ExpenseFacade;
import com.example.roshdeandishe.finance.ExpenseStatus;
import com.example.roshdeandishe.finance.ExpenseStatusChangedEvent;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
class ExpenseFacadeImpl implements ExpenseFacade {

    /** سقف دامنه‌ای پیوست - جدا از سقف servlet که بالاتر از این تنظیم شده (رجوع به application.yml). */
    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    private static final List<String> DEFAULT_CATEGORY_NAMES = List.of(
            "حقوق و دستمزد", "قبوض", "تعمیرات", "تجهیزات آموزشی", "مواد مصرفی",
            "خدمات نظافت", "پذیرایی", "سرویس مدرسه", "تبلیغات", "مالیات",
            "بیمه", "اجاره", "فوق برنامه", "سایر"
    );

    private final ExpenseCategoryRepository categoryRepository;
    private final VendorRepository vendorRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseAttachmentRepository attachmentRepository;
    private final ExpenseAttachmentContentRepository attachmentContentRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;

    ExpenseFacadeImpl(
            ExpenseCategoryRepository categoryRepository,
            VendorRepository vendorRepository,
            ExpenseRepository expenseRepository,
            ExpenseAttachmentRepository attachmentRepository,
            ExpenseAttachmentContentRepository attachmentContentRepository,
            PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher,
            IamFacade iamFacade
    ) {
        this.categoryRepository = categoryRepository;
        this.vendorRepository = vendorRepository;
        this.expenseRepository = expenseRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentContentRepository = attachmentContentRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.iamFacade = iamFacade;
    }

    @Override
    public List<CategoryInfo> listCategories(Long schoolId) {
        return categoryRepository.findBySchoolIdOrderByNameAsc(schoolId).stream()
                .map(ExpenseFacadeImpl::toCategoryInfo)
                .toList();
    }

    @Override
    @Transactional
    public CategoryInfo createCategory(Long schoolId, String name) {
        String trimmed = requireNonBlank(name, "نام دسته الزامی است.");
        return toCategoryInfo(categoryRepository.save(new ExpenseCategoryEntity(schoolId, trimmed)));
    }

    @Override
    @Transactional
    public CategoryInfo updateCategory(Long schoolId, Long categoryId, String name, Boolean active) {
        ExpenseCategoryEntity category = requireCategory(schoolId, categoryId);
        if (name != null) {
            category.setName(requireNonBlank(name, "نام دسته الزامی است."));
        }
        if (active != null) {
            category.setActive(active);
        }
        return toCategoryInfo(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public int seedDefaultCategories(Long schoolId) {
        int created = 0;
        for (String name : DEFAULT_CATEGORY_NAMES) {
            if (categoryRepository.findBySchoolIdAndName(schoolId, name).isEmpty()) {
                categoryRepository.save(new ExpenseCategoryEntity(schoolId, name));
                created++;
            }
        }
        return created;
    }

    @Override
    public List<VendorInfo> listVendors(Long schoolId) {
        return vendorRepository.findBySchoolIdOrderByNameAsc(schoolId).stream()
                .map(ExpenseFacadeImpl::toVendorInfo)
                .toList();
    }

    @Override
    @Transactional
    public VendorInfo createVendor(Long schoolId, String name, String phone, String nationalId,
                                   String economicCode, String address) {
        String trimmed = requireNonBlank(name, "نام طرف حساب الزامی است.");
        return toVendorInfo(vendorRepository.save(
                new VendorEntity(schoolId, trimmed, phone, nationalId, economicCode, address)));
    }

    @Override
    @Transactional
    public VendorInfo updateVendor(Long schoolId, Long vendorId, String name, String phone,
                                   String nationalId, String economicCode, String address, Boolean active) {
        VendorEntity vendor = requireVendor(schoolId, vendorId);
        if (name != null) {
            vendor.setName(requireNonBlank(name, "نام طرف حساب الزامی است."));
        }
        if (phone != null) vendor.setPhone(phone);
        if (nationalId != null) vendor.setNationalId(nationalId);
        if (economicCode != null) vendor.setEconomicCode(economicCode);
        if (address != null) vendor.setAddress(address);
        if (active != null) vendor.setActive(active);
        return toVendorInfo(vendorRepository.save(vendor));
    }

    @Override
    @Transactional
    public ExpenseInfo createExpense(Long schoolId, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                                     Long vendorId, PaymentMethod paymentMethod, String description,
                                     String receiptNumber, String invoiceNumber, String documentNumber) {
        validateExpenseFields(expenseDate, amount, paymentMethod);
        requireCategory(schoolId, categoryId);
        if (vendorId != null) {
            requireVendor(schoolId, vendorId);
        }

        Instant now = Instant.now();
        ExpenseEntity expense = expenseRepository.save(new ExpenseEntity(
                schoolId, expenseDate, amount, categoryId, vendorId, paymentMethod, description,
                receiptNumber, invoiceNumber, documentNumber, currentActorOrNull(), now
        ));

        eventPublisher.publishEvent(new ExpenseStatusChangedEvent(
                schoolId, expense.getId(), null, ExpenseStatus.DRAFT, amount, currentActorOrNull(), now
        ));

        return toExpenseInfo(expense);
    }

    @Override
    @Transactional
    public ExpenseInfo updateExpense(Long schoolId, Long expenseId, LocalDate expenseDate, BigDecimal amount,
                                     Long categoryId, Long vendorId, PaymentMethod paymentMethod, String description,
                                     String receiptNumber, String invoiceNumber, String documentNumber) {
        ExpenseEntity expense = requireExpense(schoolId, expenseId);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new FinanceException(new FinanceError.ExpenseNotEditable(expenseId, expense.getStatus()));
        }
        validateExpenseFields(expenseDate, amount, paymentMethod);
        requireCategory(schoolId, categoryId);
        if (vendorId != null) {
            requireVendor(schoolId, vendorId);
        }

        expense.setExpenseDate(expenseDate);
        expense.setAmount(amount);
        expense.setCategoryId(categoryId);
        expense.setVendorId(vendorId);
        expense.setPaymentMethod(paymentMethod);
        expense.setDescription(description);
        expense.setReceiptNumber(receiptNumber);
        expense.setInvoiceNumber(invoiceNumber);
        expense.setDocumentNumber(documentNumber);
        expense.setUpdatedAt(Instant.now());

        return toExpenseInfo(expenseRepository.save(expense));
    }

    @Override
    public List<ExpenseInfo> listExpenses(Long schoolId, LocalDate from, LocalDate to, Long categoryId,
                                          Long vendorId, ExpenseStatus status, PaymentMethod paymentMethod) {
        return expenseRepository.findFiltered(schoolId, from, to, categoryId, vendorId, status, paymentMethod)
                .stream()
                .map(ExpenseFacadeImpl::toExpenseInfo)
                .toList();
    }

    @Override
    public ExpenseInfo getExpense(Long schoolId, Long expenseId) {
        return toExpenseInfo(requireExpense(schoolId, expenseId));
    }

    @Override
    @Transactional
    public ExpenseInfo approveExpense(Long schoolId, Long expenseId) {
        return toExpenseInfo(transition(requireExpense(schoolId, expenseId), ExpenseStatus.APPROVED));
    }

    @Override
    @Transactional
    public ExpenseInfo payExpense(Long schoolId, Long expenseId) {
        return toExpenseInfo(transition(requireExpense(schoolId, expenseId), ExpenseStatus.PAID));
    }

    @Override
    @Transactional
    public ExpenseInfo cancelExpense(Long schoolId, Long expenseId) {
        return toExpenseInfo(transition(requireExpense(schoolId, expenseId), ExpenseStatus.CANCELLED));
    }

    /**
     * ماشین حالت یک‌جا: DRAFT→APPROVED, APPROVED→PAID, DRAFT|APPROVED→CANCELLED.
     * PAID/CANCELLED نهایی‌اند - هیچ گذاری از آن‌ها مجاز نیست.
     */
    private ExpenseEntity transition(ExpenseEntity expense, ExpenseStatus target) {
        ExpenseStatus current = expense.getStatus();
        boolean valid = switch (current) {
            case DRAFT -> target == ExpenseStatus.APPROVED || target == ExpenseStatus.CANCELLED;
            case APPROVED -> target == ExpenseStatus.PAID || target == ExpenseStatus.CANCELLED;
            case PAID, CANCELLED -> false;
        };
        if (!valid) {
            throw new FinanceException(new FinanceError.InvalidExpenseStatusTransition(
                    expense.getId(), current, target));
        }

        Instant now = Instant.now();
        expense.setStatus(target);
        expense.setUpdatedAt(now);
        ExpenseEntity saved = expenseRepository.save(expense);

        eventPublisher.publishEvent(new ExpenseStatusChangedEvent(
                expense.getSchoolId(), expense.getId(), current, target, expense.getAmount(),
                currentActorOrNull(), now
        ));

        return saved;
    }

    @Override
    @Transactional
    public AttachmentInfo addAttachment(Long schoolId, Long expenseId, String fileName, String contentType,
                                        byte[] content) {
        ExpenseEntity expense = requireExpense(schoolId, expenseId);
        if (expense.getStatus() == ExpenseStatus.CANCELLED) {
            throw new FinanceException(new FinanceError.ExpenseNotEditable(expenseId, expense.getStatus()));
        }
        if (content == null || content.length == 0) {
            throw new FinanceException(new FinanceError.InvalidAttachment("فایل پیوست خالی است."));
        }
        if (content.length > MAX_ATTACHMENT_BYTES) {
            throw new FinanceException(new FinanceError.InvalidAttachment(
                    "حجم فایل نباید از " + (MAX_ATTACHMENT_BYTES / (1024 * 1024)) + " مگابایت بیشتر باشد."));
        }
        String trimmedName = requireNonBlank(fileName, "نام فایل الزامی است.");

        ExpenseAttachmentEntity attachment = attachmentRepository.save(new ExpenseAttachmentEntity(
                schoolId, expenseId, trimmedName, contentType, content.length, currentActorOrNull(), Instant.now()
        ));
        attachmentContentRepository.save(new ExpenseAttachmentContentEntity(attachment.getId(), content));

        return toAttachmentInfo(attachment);
    }

    @Override
    public List<AttachmentInfo> listAttachments(Long schoolId, Long expenseId) {
        requireExpense(schoolId, expenseId);
        return attachmentRepository.findByExpenseIdOrderByUploadedAtAsc(expenseId).stream()
                .map(ExpenseFacadeImpl::toAttachmentInfo)
                .toList();
    }

    @Override
    public AttachmentContent loadAttachmentContent(Long schoolId, Long expenseId, Long attachmentId) {
        requireExpense(schoolId, expenseId);
        ExpenseAttachmentEntity attachment = requireAttachment(expenseId, attachmentId);
        byte[] content = attachmentContentRepository.findByAttachmentId(attachmentId)
                .map(ExpenseAttachmentContentEntity::getContent)
                .orElseThrow(() -> new FinanceException(new FinanceError.AttachmentNotFound(attachmentId)));
        return new AttachmentContent(attachment.getFileName(), attachment.getContentType(), content);
    }

    @Override
    @Transactional
    public void deleteAttachment(Long schoolId, Long expenseId, Long attachmentId) {
        requireExpense(schoolId, expenseId);
        requireAttachment(expenseId, attachmentId);
        attachmentContentRepository.deleteByAttachmentId(attachmentId);
        attachmentRepository.deleteById(attachmentId);
    }

    @Override
    public List<CategorySummary> summaryByCategory(Long schoolId, LocalDate from, LocalDate to) {
        Map<Long, String> categoryNames = categoryRepository.findBySchoolIdOrderByNameAsc(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(ExpenseCategoryEntity::getId, ExpenseCategoryEntity::getName));
        return expenseRepository.sumByCategory(schoolId, from, to).stream()
                .map(row -> new CategorySummary(
                        row.groupId(),
                        categoryNames.getOrDefault(row.groupId(), "دسته‌ی #" + row.groupId()),
                        row.total(),
                        row.count()
                ))
                .toList();
    }

    @Override
    public List<VendorSummary> summaryByVendor(Long schoolId, LocalDate from, LocalDate to) {
        Map<Long, String> vendorNames = vendorRepository.findBySchoolIdOrderByNameAsc(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(VendorEntity::getId, VendorEntity::getName));
        return expenseRepository.sumByVendor(schoolId, from, to).stream()
                .map(row -> new VendorSummary(
                        row.groupId(),
                        // هزینه‌ی بدون طرف حساب: groupId خودش null است (LEFT کوئری group by روی vendorId)
                        row.groupId() == null ? "بدون طرف حساب" : vendorNames.getOrDefault(row.groupId(), "طرف حساب #" + row.groupId()),
                        row.total(),
                        row.count()
                ))
                .toList();
    }

    @Override
    public List<MethodSummary> summaryByMethod(Long schoolId, LocalDate from, LocalDate to) {
        return expenseRepository.sumByMethod(schoolId, from, to).stream()
                .map(row -> new MethodSummary(row.method(), row.total(), row.count()))
                .toList();
    }

    @Override
    public FinanceSummary financeSummary(Long schoolId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
        BigDecimal income = paymentRepository.sumBySchoolIdAndPaidAtBetween(schoolId, fromInstant, toInstant);
        BigDecimal expense = expenseRepository.sumTotal(schoolId, from, to);
        return new FinanceSummary(income, expense);
    }

    private void validateExpenseFields(LocalDate expenseDate, BigDecimal amount, PaymentMethod paymentMethod) {
        if (expenseDate == null) {
            throw new FinanceException(new FinanceError.InvalidExpense("تاریخ هزینه الزامی است."));
        }
        if (amount == null || amount.signum() <= 0) {
            throw new FinanceException(new FinanceError.InvalidExpense("مبلغ هزینه باید یک عدد مثبت باشد."));
        }
        if (paymentMethod == null) {
            throw new FinanceException(new FinanceError.InvalidExpense("روش پرداخت الزامی است."));
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new FinanceException(new FinanceError.InvalidExpense(message));
        }
        return value.trim();
    }

    private ExpenseCategoryEntity requireCategory(Long schoolId, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> c.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new FinanceException(new FinanceError.ExpenseCategoryNotFound(categoryId)));
    }

    private VendorEntity requireVendor(Long schoolId, Long vendorId) {
        return vendorRepository.findById(vendorId)
                .filter(v -> v.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new FinanceException(new FinanceError.VendorNotFound(vendorId)));
    }

    private ExpenseEntity requireExpense(Long schoolId, Long expenseId) {
        return expenseRepository.findById(expenseId)
                .filter(e -> e.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new FinanceException(new FinanceError.ExpenseNotFound(expenseId)));
    }

    private ExpenseAttachmentEntity requireAttachment(Long expenseId, Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .filter(a -> a.getExpenseId().equals(expenseId))
                .orElseThrow(() -> new FinanceException(new FinanceError.AttachmentNotFound(attachmentId)));
    }

    private static CategoryInfo toCategoryInfo(ExpenseCategoryEntity entity) {
        return new CategoryInfo(entity.getId(), entity.getSchoolId(), entity.getName(), entity.isActive());
    }

    private static VendorInfo toVendorInfo(VendorEntity entity) {
        return new VendorInfo(entity.getId(), entity.getSchoolId(), entity.getName(), entity.getPhone(),
                entity.getNationalId(), entity.getEconomicCode(), entity.getAddress(), entity.isActive());
    }

    private static ExpenseInfo toExpenseInfo(ExpenseEntity entity) {
        return new ExpenseInfo(entity.getId(), entity.getSchoolId(), entity.getExpenseDate(), entity.getAmount(),
                entity.getCategoryId(), entity.getVendorId(), entity.getPaymentMethod(), entity.getStatus(),
                entity.getDescription(), entity.getReceiptNumber(), entity.getInvoiceNumber(),
                entity.getDocumentNumber(), entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static AttachmentInfo toAttachmentInfo(ExpenseAttachmentEntity entity) {
        return new AttachmentInfo(entity.getId(), entity.getExpenseId(), entity.getFileName(),
                entity.getContentType(), entity.getSizeBytes(), entity.getUploadedAt());
    }

    /**
     * همان قرارداد currentActorOrNull در FinanceFacadeImpl - این متد هم از
     * درخواست HTTP احراز هویت‌شده و هم از مسیرهای بدون کاربر صدا زده می‌شود.
     */
    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
