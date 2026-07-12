package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);

    /**
     * جمع پرداخت‌های یک مدرسه در یک بازه - سمتِ «درآمد» گزارش سود و زیان
     * (ExpenseFacade.financeSummary). coalesce چون sum روی مجموعه‌ی خالی
     * null برمی‌گرداند، نه صفر.
     */
    @Query("""
            select coalesce(sum(p.amount), 0)
            from PaymentEntity p
            where p.schoolId = :schoolId and p.paidAt between :from and :to
            """)
    BigDecimal sumBySchoolIdAndPaidAtBetween(
            @Param("schoolId") Long schoolId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
