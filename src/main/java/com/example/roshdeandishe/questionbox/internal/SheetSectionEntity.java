package com.example.roshdeandishe.questionbox.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sheet_sections", schema = "questionbox")
@Getter
@NoArgsConstructor
class SheetSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String instruction;

    SheetSectionEntity(Long sheetId, int position, String title, String instruction) {
        this.sheetId = sheetId;
        this.position = position;
        this.title = title;
        this.instruction = instruction;
    }
}
