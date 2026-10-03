package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * فعالیتِ فوق‌برنامه‌ی برنامهٔ کلاسی. {@code schoolId == null} یعنی پیش‌فرضِ
 * پلتفرم (سراسری؛ برنامه فقط می‌خواند و اپراتور با مهاجرت داده نگه می‌دارد)،
 * وگرنه فعالیتِ خودِ همان مدرسه.
 */
@Entity
@Table(name = "activities", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class ActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(nullable = false, length = 128)
    private String name;

    ActivityEntity(Long schoolId, String name) {
        this.schoolId = schoolId;
        this.name = name;
    }
}
