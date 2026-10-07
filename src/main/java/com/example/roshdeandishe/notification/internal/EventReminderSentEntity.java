package com.example.roshdeandishe.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * دفتر «این رویداد برای این کاربر یادآوری شد» (bale.event_reminders_sent، V19). هر دو شناسه فقط
 * عددی و مات‌اند (بدون FK به school/iam). نوشتن فقط از راه {@link EventReminderSentRepository#claim}
 * است (درجِ اتمیک)، نه save().
 */
@Entity
@Table(name = "event_reminders_sent", schema = "bale")
@IdClass(EventReminderSentEntity.Key.class)
@Getter
@NoArgsConstructor
class EventReminderSentEntity {

    @Id
    @Column(name = "calendar_entry_id", nullable = false)
    private Long calendarEntryId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sent_at", nullable = false, insertable = false, updatable = false)
    private Instant sentAt;

    @EqualsAndHashCode
    @NoArgsConstructor
    static class Key implements Serializable {
        private Long calendarEntryId;
        private Long userId;
    }
}
