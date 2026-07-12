package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ExpenseAttachmentRepository extends JpaRepository<ExpenseAttachmentEntity, Long> {

    List<ExpenseAttachmentEntity> findByExpenseIdOrderByUploadedAtAsc(Long expenseId);
}
