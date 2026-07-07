package com.example.roshdeandishe.bale.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * پیوند «این User، این chatId را در بله دارد». userId فقط یک شناسه‌ی
 * عددی مات از دید bale است (بدون FK به iam)، دقیقاً همان قرارداد
 * academic.StudentEntity.userId.
 * <p>
 * یک ردیف به‌ازای هر User (نه هر chat) - اگر همان کاربر از یک chatId
 * دیگر دوباره شماره‌اش را به اشتراک بگذارد، همین ردیف به‌روزرسانی
 * می‌شود، نه ردیف تازه (رجوع به BaleFacadeImpl.linkChat).
 */
@Entity
@Table(name = "bale_links", schema = "bale")
@Getter
@Setter
@NoArgsConstructor
class BaleLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    BaleLinkEntity(Long userId, Long chatId, Instant linkedAt) {
        this.userId = userId;
        this.chatId = chatId;
        this.linkedAt = linkedAt;
    }
}
