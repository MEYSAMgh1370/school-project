package com.example.roshdeandishe.finance.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * عمداً فقط {@code save} و کوئری‌های خواندنی: audit append-only است و هیچ مسیرِ حذف/ویرایشی از طریق این
 * repository وجود ندارد.
 */
interface FinanceAuditRepository extends Repository<FinanceAuditEntity, Long> {

    FinanceAuditEntity save(FinanceAuditEntity entity);

    /**
     * فهرستِ صفحه‌بندی‌شده‌ی یک مدرسه، جدیدترین اول. فیلترهای تهی نادیده‌اند؛ بازه‌ی زمانی همیشه مقدار دارد
     * ({@code from} شاملِ خودش، {@code toExclusive} بدونِ خودش) تا پارامترِ تهیِ زمانی روی Postgres مشکل نسازد.
     */
    @Query("""
            select a from FinanceAuditEntity a
            where a.schoolId = :schoolId
              and (:invoiceId is null or a.invoiceId = :invoiceId)
              and (:studentId is null or a.studentId = :studentId)
              and (:action is null or a.action = :action)
              and a.occurredAt >= :from and a.occurredAt < :toExclusive
            order by a.occurredAt desc, a.id desc
            """)
    Page<FinanceAuditEntity> search(@Param("schoolId") Long schoolId, @Param("invoiceId") Long invoiceId,
                                    @Param("studentId") Long studentId, @Param("action") String action,
                                    @Param("from") Instant from, @Param("toExclusive") Instant toExclusive,
                                    Pageable pageable);
}
