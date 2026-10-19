package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

    List<InvoiceEntity> findBySchoolIdOrderByIssuedAtDesc(Long schoolId);

    List<InvoiceEntity> findBySchoolIdAndStudentIdInOrderByIssuedAtDesc(Long schoolId, Collection<Long> studentIds);

    List<InvoiceEntity> findBySchoolIdAndStudentId(Long schoolId, Long studentId);

    /** فاکتورهای یک سال به‌علاوه‌ی «بدون سال»ها (تا نسبت داده شوند). */
    @Query("select i from InvoiceEntity i where i.schoolId = :schoolId "
            + "and (i.academicYear = :academicYear or i.academicYear is null) order by i.issuedAt desc")
    List<InvoiceEntity> findForYearOrUnattributed(@Param("schoolId") Long schoolId,
                                                  @Param("academicYear") String academicYear);

    /**
     * همان، برای زیرمجموعه‌ای از دانش‌آموزان (دیدِ خانواده): سالِ جاری، «بدون سال»، و فاکتورِ سال‌های
     * <b>قبل</b> که هنوز مانده دارد - بدهی نباید از خانواده پنهان بماند (academic-year.md فاز ۲). مقایسه‌ی
     * «قبل» با ترتیبِ حروفِ کدِ canonical درست است («1404-1405» &lt; «1405-1406»).
     */
    @Query("select i from InvoiceEntity i where i.schoolId = :schoolId and i.studentId in :studentIds "
            + "and (i.academicYear = :academicYear or i.academicYear is null "
            + "or (i.academicYear < :academicYear and i.amountPaid < i.amount - i.discountTotal)) order by i.issuedAt desc")
    List<InvoiceEntity> findForStudentsInYearOrUnattributed(@Param("schoolId") Long schoolId,
                                                            @Param("studentIds") Collection<Long> studentIds,
                                                            @Param("academicYear") String academicYear);

    int countBySchoolIdAndAcademicYearIsNull(Long schoolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceEntity i where i.id = :invoiceId and i.schoolId = :schoolId")
    Optional<InvoiceEntity> findByIdAndSchoolIdForUpdate(@Param("schoolId") Long schoolId,
                                                         @Param("invoiceId") Long invoiceId);

    /** قفل‌گذاریِ فاکتور بدونِ دانستنِ مدرسه (sweepِ تخفیف‌های مهلت‌گذشته). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceEntity i where i.id = :invoiceId")
    Optional<InvoiceEntity> findByIdForUpdate(@Param("invoiceId") Long invoiceId);

    /**
     * {@code on conflict do nothing} بدونِ هدف: یکتایی حالا دو ایندکسِ جزئی است (V10: با سال / بدون سال)
     * و هر دو باید همین «از قبل هست» را بدهند.
     */
    @Modifying
    @Query(value = """
            insert into finance.invoices (school_id, student_id, amount, issued_at, due_date, amount_paid, academic_year)
            values (:schoolId, :studentId, :amount, :issuedAt, :dueDate, 0, :academicYear)
            on conflict do nothing
            """, nativeQuery = true)
    int insertTuitionInvoiceIfAbsent(@Param("schoolId") Long schoolId,
                                     @Param("studentId") Long studentId,
                                     @Param("amount") BigDecimal amount,
                                     @Param("issuedAt") Instant issuedAt,
                                     @Param("dueDate") Instant dueDate,
                                     @Param("academicYear") String academicYear);

    /**
     * دانش‌آموزانی که برای این سال فاکتور دارند - سمت «موجود» در محاسبه‌ی backfill
     * (FinanceFacade.backfillInvoices).
     */
    @Query("select distinct i.studentId from InvoiceEntity i where i.schoolId = :schoolId "
            + "and i.academicYear = :academicYear")
    List<Long> findDistinctStudentIdsBySchoolIdAndAcademicYear(@Param("schoolId") Long schoolId,
                                                               @Param("academicYear") String academicYear);

    /** همان برای «بدون سال» (مدرسه‌ای که سالِ جاری ندارد). */
    @Query("select distinct i.studentId from InvoiceEntity i where i.schoolId = :schoolId "
            + "and i.academicYear is null")
    List<Long> findDistinctUnattributedStudentIdsBySchoolId(@Param("schoolId") Long schoolId);

    /** دانش‌آموزانی که هم فاکتورِ «بدون سال» دارند و هم فاکتورِ این سال: نسبت دادن تداخل می‌کرد. */
    @Query("select count(distinct legacy.studentId) from InvoiceEntity legacy where legacy.schoolId = :schoolId "
            + "and legacy.academicYear is null and exists (select 1 from InvoiceEntity dated "
            + "where dated.schoolId = :schoolId and dated.studentId = legacy.studentId "
            + "and dated.academicYear = :academicYear)")
    long countAttributionConflicts(@Param("schoolId") Long schoolId, @Param("academicYear") String academicYear);

    @Modifying
    @Query("update InvoiceEntity i set i.academicYear = :academicYear "
            + "where i.schoolId = :schoolId and i.academicYear is null")
    int attributeToYear(@Param("schoolId") Long schoolId, @Param("academicYear") String academicYear);
}
