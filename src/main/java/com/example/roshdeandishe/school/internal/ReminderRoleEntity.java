package com.example.roshdeandishe.school.internal;

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

/**
 * یک نقش از نقش‌هایی که مدیر مدرسه برای دریافت یادآوریِ رویدادها انتخاب کرده است
 * (openspec/changes/event-reminders.md). نبودنِ هر ردیفی برای مدرسه یعنی همه‌ی اعضای فعال.
 */
@Entity
@Table(name = "reminder_roles", schema = "school")
@IdClass(ReminderRoleEntity.Key.class)
@Getter
@NoArgsConstructor
class ReminderRoleEntity {

    @Id
    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Id
    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    ReminderRoleEntity(Long schoolId, String roleCode) {
        this.schoolId = schoolId;
        this.roleCode = roleCode;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    static class Key implements Serializable {
        private Long schoolId;
        private String roleCode;
    }
}
