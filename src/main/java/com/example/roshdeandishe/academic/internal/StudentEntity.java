package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * این Entity هویت دانش‌آموز نیست - آن {@code UserEntity} در ماژول iam است
 * (طبق CLAUDE.md §4: Student یک نقش است، نه یک نوع هویت جدا). این فقط
 * داده‌ی مختص academic برای همان کاربر را نگه می‌دارد: در کدام مدرسه، در
 * کدام کلاس، و از چه زمانی ثبت‌نام شده.
 * <p>
 * {@code userId} به UserEntity.id در iam اشاره دارد اما FK واقعی نیست -
 * academic هرگز نباید مستقیماً به جداول داخلی iam وابسته شود؛ این فقط یک
 * شناسه‌ی عددی opaque از دید academic است، دقیقاً مثل الگوی schoolId.
 */
@Entity
@Table(name = "students", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    StudentEntity(Long userId, Long schoolId, Long classId, Instant enrolledAt) {
        this.userId = userId;
        this.schoolId = schoolId;
        this.classId = classId;
        this.enrolledAt = enrolledAt;
    }
}
