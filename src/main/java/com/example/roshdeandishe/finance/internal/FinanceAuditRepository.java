package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface FinanceAuditRepository extends JpaRepository<FinanceAuditEntity, Long> {
}
