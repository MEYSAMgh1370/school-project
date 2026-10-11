package com.example.roshdeandishe.curriculum.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک سرفصل در تاکسونومی سلسله‌مراتبی مدرسه. adjacency list ساده:
 * {@code parentId} به یک TopicEntity دیگرِ همین ماژول اشاره دارد
 * (null = ریشه). عمداً بازنمایی دوگانه‌ی moshavere (ltree + درخت pickle
 * شده) پورت نشده - MOSHAVERE_PORT_SPEC.md §4/§7.
 * <p>
 * ستون {@code sort_order} (نه {@code order} که کلیدواژه‌ی SQL است) ترتیب
 * نمایش بین هم‌نیاهاست.
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

    /** null = سرفصل پلتفرمی (برای همه‌ی مدرسه‌ها قابل‌مشاهده). */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "source_code", length = 32)
    private String sourceCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 250)
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
