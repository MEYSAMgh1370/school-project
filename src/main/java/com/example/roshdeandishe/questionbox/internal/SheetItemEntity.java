package com.example.roshdeandishe.questionbox.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** آیتمِ یک بخش: دقیقاً یکی از {@code boxQuestionId} یا {@code bankQuestionId} (قید در migration). */
@Entity
@Table(name = "sheet_items", schema = "questionbox")
@Getter
@NoArgsConstructor
class SheetItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(nullable = false)
    private int position;

    @Column(name = "box_question_id")
    private Long boxQuestionId;

    @Column(name = "bank_question_id")
    private Long bankQuestionId;

    SheetItemEntity(Long sectionId, int position, Long boxQuestionId, Long bankQuestionId) {
        this.sectionId = sectionId;
        this.position = position;
        this.boxQuestionId = boxQuestionId;
        this.bankQuestionId = bankQuestionId;
    }
}
