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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceEntity i where i.id = :invoiceId and i.schoolId = :schoolId")
    Optional<InvoiceEntity> findByIdAndSchoolIdForUpdate(@Param("schoolId") Long schoolId,
                                                         @Param("invoiceId") Long invoiceId);

    @Modifying
    @Query(value = """
            insert into finance.invoices (school_id, student_id, amount, issued_at, due_date, amount_paid)
            values (:schoolId, :studentId, :amount, :issuedAt, :dueDate, 0)
            on conflict (school_id, student_id) do nothing
            """, nativeQuery = true)
    int insertTuitionInvoiceIfAbsent(@Param("schoolId") Long schoolId,
                                     @Param("studentId") Long studentId,
                                     @Param("amount") BigDecimal amount,
                                     @Param("issuedAt") Instant issuedAt,
                                     @Param("dueDate") Instant dueDate);

    /**
     * دانش‌آموزانی که در این مدرسه دست‌کم یک فاکتور دارند - سمت «موجود» در
     * محاسبه‌ی backfill (FinanceFacade.backfillInvoices).
     */
    @Query("select distinct i.studentId from InvoiceEntity i where i.schoolId = :schoolId")
    List<Long> findDistinctStudentIdsBySchoolId(@Param("schoolId") Long schoolId);
}
