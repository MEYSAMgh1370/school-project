package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

    List<InvoiceEntity> findBySchoolIdOrderByIssuedAtDesc(Long schoolId);

    List<InvoiceEntity> findBySchoolIdAndStudentIdInOrderByIssuedAtDesc(Long schoolId, Collection<Long> studentIds);

    /**
     * دانش‌آموزانی که در این مدرسه دست‌کم یک فاکتور دارند - سمت «موجود» در
     * محاسبه‌ی backfill (FinanceFacade.backfillInvoices).
     */
    @Query("select distinct i.studentId from InvoiceEntity i where i.schoolId = :schoolId")
    List<Long> findDistinctStudentIdsBySchoolId(@Param("schoolId") Long schoolId);
}
