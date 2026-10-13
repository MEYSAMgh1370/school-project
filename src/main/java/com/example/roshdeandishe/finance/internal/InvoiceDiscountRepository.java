package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.DiscountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface InvoiceDiscountRepository extends JpaRepository<InvoiceDiscountEntity, Long> {

    List<InvoiceDiscountEntity> findByInvoiceIdOrderByCreatedAtAscIdAsc(Long invoiceId);

    List<InvoiceDiscountEntity> findByInvoiceIdAndStatusOrderByCreatedAtAscIdAsc(Long invoiceId, DiscountStatus status);

    /** تخفیف‌های مشروطِ بازِ چند فاکتور با یک query (پاسخِ خودسرویس؛ بدون N+1). */
    List<InvoiceDiscountEntity> findBySchoolIdAndInvoiceIdInAndStatus(
            Long schoolId, Collection<Long> invoiceIds, DiscountStatus status);

    /**
     * فاکتورهایی که تخفیفِ PENDINGِ مهلت‌گذشته دارند. مرزِ {@code <=} عمداً محافظه‌کارانه است (روزِ مدرسه ممکن است
     * از روزِ دیتابیس جلوتر باشد)؛ بررسیِ دقیقِ منطقه‌ی زمانی زیر قفل در lapseExpiredDiscounts انجام می‌شود.
     */
    @Query("select distinct d.invoiceId from InvoiceDiscountEntity d "
            + "where d.status = :status and d.deadline <= current_date")
    List<Long> findInvoiceIdsWithCandidateExpiredDiscounts(@Param("status") DiscountStatus status);
}
