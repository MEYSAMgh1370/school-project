package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.ExpenseStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    /**
     * فهرست فیلترشده - همه‌ی فیلترها اختیاری (null یعنی بدون فیلتر). یک
     * کوئری صریح به‌جای Specification/QueryDSL: چهار فیلتر ساده، ابزار
     * داینامیک لازم ندارد.
     */
    @Query("""
            select e from ExpenseEntity e
            where e.schoolId = :schoolId
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
              and (:categoryId is null or e.categoryId = :categoryId)
              and (:vendorId is null or e.vendorId = :vendorId)
              and (:status is null or e.status = :status)
              and (:method is null or e.paymentMethod = :method)
            order by e.expenseDate desc, e.id desc
            """)
    List<ExpenseEntity> findFiltered(
            @Param("schoolId") Long schoolId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("categoryId") Long categoryId,
            @Param("vendorId") Long vendorId,
            @Param("status") ExpenseStatus status,
            @Param("method") PaymentMethod method
    );

    /**
     * سه projection تجمیعی گزارش‌ها. قاعده‌ی ثابت (بدون پارامتر status تا
     * وقتی کسی بخواهد): فقط APPROVED و PAID - DRAFT هنوز تأیید نشده و
     * CANCELLED باطل است؛ گزارشی که با پیش‌نویس تکان بخورد گمراه‌کننده است.
     */
    record GroupSum(Long groupId, BigDecimal total, long count) {
    }

    record MethodSum(PaymentMethod method, BigDecimal total, long count) {
    }

    @Query("""
            select new com.example.roshdeandishe.finance.internal.ExpenseRepository$GroupSum(
                e.categoryId, sum(e.amount), count(e))
            from ExpenseEntity e
            where e.schoolId = :schoolId and e.expenseDate between :from and :to
              and e.status in (com.example.roshdeandishe.finance.ExpenseStatus.APPROVED,
                               com.example.roshdeandishe.finance.ExpenseStatus.PAID)
            group by e.categoryId
            """)
    List<GroupSum> sumByCategory(@Param("schoolId") Long schoolId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select new com.example.roshdeandishe.finance.internal.ExpenseRepository$GroupSum(
                e.vendorId, sum(e.amount), count(e))
            from ExpenseEntity e
            where e.schoolId = :schoolId and e.expenseDate between :from and :to
              and e.status in (com.example.roshdeandishe.finance.ExpenseStatus.APPROVED,
                               com.example.roshdeandishe.finance.ExpenseStatus.PAID)
            group by e.vendorId
            """)
    List<GroupSum> sumByVendor(@Param("schoolId") Long schoolId,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select new com.example.roshdeandishe.finance.internal.ExpenseRepository$MethodSum(
                e.paymentMethod, sum(e.amount), count(e))
            from ExpenseEntity e
            where e.schoolId = :schoolId and e.expenseDate between :from and :to
              and e.status in (com.example.roshdeandishe.finance.ExpenseStatus.APPROVED,
                               com.example.roshdeandishe.finance.ExpenseStatus.PAID)
            group by e.paymentMethod
            """)
    List<MethodSum> sumByMethod(@Param("schoolId") Long schoolId,
                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from ExpenseEntity e
            where e.schoolId = :schoolId and e.expenseDate between :from and :to
              and e.status in (com.example.roshdeandishe.finance.ExpenseStatus.APPROVED,
                               com.example.roshdeandishe.finance.ExpenseStatus.PAID)
            """)
    BigDecimal sumTotal(@Param("schoolId") Long schoolId,
                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsBySchoolIdAndCategoryId(Long schoolId, Long categoryId);
}
