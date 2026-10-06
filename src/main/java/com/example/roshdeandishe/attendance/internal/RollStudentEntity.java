package com.example.roshdeandishe.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * عضوِ یک roll: دانش‌آموزی که در لحظه‌ی ثبتِ حضور و غیابِ این (کلاس، روز) در کلاس بود. مبنای «چند نفر در
 * کلاس بودند» برای آن روز است، حتی اگر بعداً کسی ثبت‌نام یا منتقل شود. rollِ بدونِ ردیف در اینجا (پیش از V14)
 * با فهرستِ زنده‌ی کلاس محاسبه می‌شود.
 */
@Entity
@Table(name = "roll_students", schema = "attendance")
@IdClass(RollStudentEntity.Key.class)
@Getter
@NoArgsConstructor
class RollStudentEntity {

    @Id
    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Id
    @Column(nullable = false)
    private LocalDate day;

    @Id
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    RollStudentEntity(Long schoolId, Long classId, LocalDate day, Long studentId) {
        this.schoolId = schoolId;
        this.classId = classId;
        this.day = day;
        this.studentId = studentId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    static class Key implements Serializable {
        private Long classId;
        private LocalDate day;
        private Long studentId;
    }
}
