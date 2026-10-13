package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface InstallmentRepository extends JpaRepository<InstallmentEntity, Long> {

    List<InstallmentEntity> findByInvoiceIdOrderByDueDateAscSeqAscIdAsc(Long invoiceId);

    /** یک query برای چند فاکتور (فهرست‌ها) تا N+1 نشود. */
    List<InstallmentEntity> findBySchoolIdAndInvoiceIdIn(Long schoolId, Collection<Long> invoiceIds);
}
