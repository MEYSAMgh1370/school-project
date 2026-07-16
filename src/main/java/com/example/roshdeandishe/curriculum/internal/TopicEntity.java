package com.example.roshdeandishe.curriculum.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک سرفصل درسی در تاکسونومی سلسله‌مراتبی یک مدرسه. {@code parentId} یک
 * خود-ارجاع nullable در همین جدول است (adjacency list ساده - نه ltree و نه
 * closure table، طبق MOSHAVERE_PORT_SPEC.md §4): null یعنی ریشه.
 * <p>
 * ستون {@code sort_order} (نه {@code order} که کلیدواژه‌ی SQL است) ترتیب
 * نمایش بین هم‌نیاها را نگه می‌دارد.
 */
@Entity
@Table(name = "topics", schema = "curriculum")
@Getter
@Setter
@NoArgsConstructor
class TopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    TopicEntity(Long schoolId, Long parentId, String name, int sortOrder) {
        this.schoolId = schoolId;
        this.parentId = parentId;
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
