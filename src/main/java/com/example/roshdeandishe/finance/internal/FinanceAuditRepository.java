package com.example.roshdeandishe.finance.internal;

import org.springframework.data.repository.Repository;

/** عمداً فقط {@code save}: audit append-only است و هیچ مسیرِ حذف/ویرایشی از طریق این repository وجود ندارد. */
interface FinanceAuditRepository extends Repository<FinanceAuditEntity, Long> {

    FinanceAuditEntity save(FinanceAuditEntity entity);
}
