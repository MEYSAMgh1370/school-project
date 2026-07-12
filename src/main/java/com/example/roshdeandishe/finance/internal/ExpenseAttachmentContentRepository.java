package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ExpenseAttachmentContentRepository extends JpaRepository<ExpenseAttachmentContentEntity, Long> {

    Optional<ExpenseAttachmentContentEntity> findByAttachmentId(Long attachmentId);

    void deleteByAttachmentId(Long attachmentId);
}
