package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** یک بار اجرای ارتقا برای یک مدرسه (academic-year.md فاز ۳)؛ برای برگرداندن (undo) نگه‌داری می‌شود. */
@Entity
@Table(name = "promotion_runs", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class PromotionRunEntity {

    enum Mode {
        MANUAL, AUTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "from_year", nullable = false, length = 16)
    private String fromYear;

    @Column(name = "to_year", nullable = false, length = 16)
    private String toYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Mode mode;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "undone_at")
    private Instant undoneAt;

    PromotionRunEntity(Long schoolId, String fromYear, String toYear, Mode mode, Long createdByUserId) {
        this.schoolId = schoolId;
        this.fromYear = fromYear;
        this.toYear = toYear;
        this.mode = mode;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }
}
