package com.example.roshdeandishe.finance;

import com.example.roshdeandishe.finance.ExpenseFacade.AttachmentContent;
import com.example.roshdeandishe.finance.ExpenseFacade.AttachmentInfo;
import com.example.roshdeandishe.finance.ExpenseFacade.CategoryInfo;
import com.example.roshdeandishe.finance.ExpenseFacade.CategorySummary;
import com.example.roshdeandishe.finance.ExpenseFacade.ExpenseInfo;
import com.example.roshdeandishe.finance.ExpenseFacade.FinanceSummary;
import com.example.roshdeandishe.finance.ExpenseFacade.MethodSummary;
import com.example.roshdeandishe.finance.ExpenseFacade.VendorInfo;
import com.example.roshdeandishe.finance.ExpenseFacade.VendorSummary;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * مدیریت هزینه‌های مدرسه (دسته‌ها، طرف‌حساب‌ها، هزینه‌ها، پیوست‌ها، گزارش‌ها) -
 * گسترش ماژول finance، نه ماژول جدید (ExpenseFacade، §2 CLAUDE.md). همان
 * الگوی دو لایه‌ی FinanceController: {@code @RequiresModule("FINANCE")}
 * کلاسی + {@code @RequiresPermission} متدی؛ هر متد اول
 * {@code TenantContext.requireMatches} می‌کند.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
@RequiresModule("FINANCE")
public class ExpenseController {

    private final ExpenseFacade expenseFacade;

    public ExpenseController(ExpenseFacade expenseFacade) {
        this.expenseFacade = expenseFacade;
    }

    @GetMapping("/expense-categories")
    @RequiresPermission("EXPENSE_VIEW")
    public List<CategoryResponse> listCategories(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return expenseFacade.listCategories(schoolId).stream().map(ExpenseController::toResponse).toList();
    }

    @PostMapping("/expense-categories")
    @RequiresPermission("EXPENSE_MANAGE")
    public CategoryResponse createCategory(@PathVariable Long schoolId, @RequestBody CategoryRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.createCategory(schoolId, request.name()));
    }

    @PutMapping("/expense-categories/{categoryId}")
    @RequiresPermission("EXPENSE_MANAGE")
    public CategoryResponse updateCategory(@PathVariable Long schoolId, @PathVariable Long categoryId,
                                           @RequestBody CategoryRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.updateCategory(schoolId, categoryId, request.name(), request.active()));
    }

    @PostMapping("/expense-categories/seed-defaults")
    @RequiresPermission("EXPENSE_MANAGE")
    public SeedDefaultsResponse seedDefaultCategories(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return new SeedDefaultsResponse(expenseFacade.seedDefaultCategories(schoolId));
    }

    @GetMapping("/vendors")
    @RequiresPermission("EXPENSE_VIEW")
    public List<VendorResponse> listVendors(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return expenseFacade.listVendors(schoolId).stream().map(ExpenseController::toResponse).toList();
    }

    @PostMapping("/vendors")
    @RequiresPermission("EXPENSE_MANAGE")
    public VendorResponse createVendor(@PathVariable Long schoolId, @RequestBody VendorRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.createVendor(schoolId, request.name(), request.phone(),
                request.nationalId(), request.economicCode(), request.address()));
    }

    @PutMapping("/vendors/{vendorId}")
    @RequiresPermission("EXPENSE_MANAGE")
    public VendorResponse updateVendor(@PathVariable Long schoolId, @PathVariable Long vendorId,
                                       @RequestBody VendorRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.updateVendor(schoolId, vendorId, request.name(), request.phone(),
                request.nationalId(), request.economicCode(), request.address(), request.active()));
    }

    @GetMapping("/expenses")
    @RequiresPermission("EXPENSE_VIEW")
    public List<ExpenseResponse> listExpenses(
            @PathVariable Long schoolId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod
    ) {
        TenantContext.requireMatches(schoolId);
        return expenseFacade.listExpenses(schoolId, from, to, categoryId, vendorId, status, paymentMethod)
                .stream().map(ExpenseController::toResponse).toList();
    }

    @PostMapping("/expenses")
    @RequiresPermission("EXPENSE_MANAGE")
    public ExpenseResponse createExpense(@PathVariable Long schoolId, @RequestBody ExpenseRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.createExpense(schoolId, request.expenseDate(), request.amount(),
                request.categoryId(), request.vendorId(), request.paymentMethod(), request.description(),
                request.receiptNumber(), request.invoiceNumber(), request.documentNumber()));
    }

    @GetMapping("/expenses/{expenseId}")
    @RequiresPermission("EXPENSE_VIEW")
    public ExpenseResponse getExpense(@PathVariable Long schoolId, @PathVariable Long expenseId) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.getExpense(schoolId, expenseId));
    }

    @PutMapping("/expenses/{expenseId}")
    @RequiresPermission("EXPENSE_MANAGE")
    public ExpenseResponse updateExpense(@PathVariable Long schoolId, @PathVariable Long expenseId,
                                         @RequestBody ExpenseRequest request) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.updateExpense(schoolId, expenseId, request.expenseDate(), request.amount(),
                request.categoryId(), request.vendorId(), request.paymentMethod(), request.description(),
                request.receiptNumber(), request.invoiceNumber(), request.documentNumber()));
    }

    @PostMapping("/expenses/{expenseId}/approve")
    @RequiresPermission("EXPENSE_APPROVE")
    public ExpenseResponse approveExpense(@PathVariable Long schoolId, @PathVariable Long expenseId) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.approveExpense(schoolId, expenseId));
    }

    @PostMapping("/expenses/{expenseId}/pay")
    @RequiresPermission("EXPENSE_MANAGE")
    public ExpenseResponse payExpense(@PathVariable Long schoolId, @PathVariable Long expenseId) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.payExpense(schoolId, expenseId));
    }

    @PostMapping("/expenses/{expenseId}/cancel")
    @RequiresPermission("EXPENSE_MANAGE")
    public ExpenseResponse cancelExpense(@PathVariable Long schoolId, @PathVariable Long expenseId) {
        TenantContext.requireMatches(schoolId);
        return toResponse(expenseFacade.cancelExpense(schoolId, expenseId));
    }

    @PostMapping(value = "/expenses/{expenseId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("EXPENSE_MANAGE")
    public AttachmentResponse uploadAttachment(@PathVariable Long schoolId, @PathVariable Long expenseId,
                                               @RequestParam("file") MultipartFile file) {
        TenantContext.requireMatches(schoolId);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return toResponse(expenseFacade.addAttachment(
                schoolId, expenseId, file.getOriginalFilename(), file.getContentType(), content));
    }

    @GetMapping("/expenses/{expenseId}/attachments")
    @RequiresPermission("EXPENSE_VIEW")
    public List<AttachmentResponse> listAttachments(@PathVariable Long schoolId, @PathVariable Long expenseId) {
        TenantContext.requireMatches(schoolId);
        return expenseFacade.listAttachments(schoolId, expenseId).stream()
                .map(ExpenseController::toResponse).toList();
    }

    @GetMapping("/expenses/{expenseId}/attachments/{attachmentId}/content")
    @RequiresPermission("EXPENSE_VIEW")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long schoolId, @PathVariable Long expenseId,
                                                     @PathVariable Long attachmentId) {
        TenantContext.requireMatches(schoolId);
        AttachmentContent content = expenseFacade.loadAttachmentContent(schoolId, expenseId, attachmentId);

        // نام فایل فارسی: RFC 5987 (filename*) چون Content-Disposition ASCII-only است
        String encodedName = java.net.URLEncoder.encode(content.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType contentType = content.contentType() != null
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(content.content());
    }

    @DeleteMapping("/expenses/{expenseId}/attachments/{attachmentId}")
    @RequiresPermission("EXPENSE_MANAGE")
    public void deleteAttachment(@PathVariable Long schoolId, @PathVariable Long expenseId,
                                 @PathVariable Long attachmentId) {
        TenantContext.requireMatches(schoolId);
        expenseFacade.deleteAttachment(schoolId, expenseId, attachmentId);
    }

    @GetMapping("/expenses/summary")
    @RequiresPermission("EXPENSE_VIEW")
    public Object expenseSummary(
            @PathVariable Long schoolId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam String groupBy
    ) {
        TenantContext.requireMatches(schoolId);
        return switch (groupBy) {
            case "category" -> expenseFacade.summaryByCategory(schoolId, from, to).stream()
                    .map(ExpenseController::toResponse).toList();
            case "vendor" -> expenseFacade.summaryByVendor(schoolId, from, to).stream()
                    .map(ExpenseController::toResponse).toList();
            case "paymentMethod" -> expenseFacade.summaryByMethod(schoolId, from, to).stream()
                    .map(ExpenseController::toResponse).toList();
            default -> throw new FinanceException(new FinanceError.InvalidExpense(
                    "groupBy باید یکی از category، vendor یا paymentMethod باشد."));
        };
    }

    @GetMapping("/finance-summary")
    @RequiresPermission("EXPENSE_VIEW")
    public FinanceSummaryResponse financeSummary(
            @PathVariable Long schoolId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        TenantContext.requireMatches(schoolId);
        FinanceSummary summary = expenseFacade.financeSummary(schoolId, from, to);
        return new FinanceSummaryResponse(summary.income(), summary.expense());
    }

    private static CategoryResponse toResponse(CategoryInfo info) {
        return new CategoryResponse(info.id(), info.name(), info.active());
    }

    private static VendorResponse toResponse(VendorInfo info) {
        return new VendorResponse(info.id(), info.name(), info.phone(), info.nationalId(),
                info.economicCode(), info.address(), info.active());
    }

    private static ExpenseResponse toResponse(ExpenseInfo info) {
        return new ExpenseResponse(info.id(), info.expenseDate(), info.amount(), info.categoryId(),
                info.vendorId(), info.paymentMethod(), info.status(), info.description(), info.receiptNumber(),
                info.invoiceNumber(), info.documentNumber(), info.createdAt(), info.updatedAt());
    }

    private static AttachmentResponse toResponse(AttachmentInfo info) {
        return new AttachmentResponse(info.id(), info.fileName(), info.contentType(), info.sizeBytes(),
                info.uploadedAt());
    }

    private static CategorySummaryResponse toResponse(CategorySummary summary) {
        return new CategorySummaryResponse(summary.categoryId(), summary.categoryName(), summary.total(),
                summary.count());
    }

    private static VendorSummaryResponse toResponse(VendorSummary summary) {
        return new VendorSummaryResponse(summary.vendorId(), summary.vendorName(), summary.total(),
                summary.count());
    }

    private static MethodSummaryResponse toResponse(MethodSummary summary) {
        return new MethodSummaryResponse(summary.method(), summary.total(), summary.count());
    }

    public record CategoryRequest(String name, Boolean active) {
    }

    public record CategoryResponse(Long id, String name, boolean active) {
    }

    public record SeedDefaultsResponse(int createdCount) {
    }

    public record VendorRequest(String name, String phone, String nationalId, String economicCode,
                                String address, Boolean active) {
    }

    public record VendorResponse(Long id, String name, String phone, String nationalId, String economicCode,
                                 String address, boolean active) {
    }

    public record ExpenseRequest(LocalDate expenseDate, BigDecimal amount, Long categoryId, Long vendorId,
                                 PaymentMethod paymentMethod, String description, String receiptNumber,
                                 String invoiceNumber, String documentNumber) {
    }

    public record ExpenseResponse(Long id, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                                  Long vendorId, PaymentMethod paymentMethod, ExpenseStatus status,
                                  String description, String receiptNumber, String invoiceNumber,
                                  String documentNumber, Instant createdAt, Instant updatedAt) {
    }

    public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes,
                                     Instant uploadedAt) {
    }

    public record CategorySummaryResponse(Long categoryId, String categoryName, BigDecimal total, long count) {
    }

    public record VendorSummaryResponse(Long vendorId, String vendorName, BigDecimal total, long count) {
    }

    public record MethodSummaryResponse(PaymentMethod method, BigDecimal total, long count) {
    }

    public record FinanceSummaryResponse(BigDecimal income, BigDecimal expense) {
    }
}
