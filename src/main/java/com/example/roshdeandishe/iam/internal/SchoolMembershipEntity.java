package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * این Entity جایگزین مدل قبلی (Teacher/Student/Parent/SchoolAdmin به‌عنوان
 * نوع‌های جدا) شده است. هر ردیف یعنی: "این User، در این School، این Role را دارد".
 * <p>
 * یک User می‌تواند چندین Membership داشته باشد (حتی در یک مدرسه با چند نقش،
 * یا در چند مدرسه با نقش‌های متفاوت). مثال از مستندات کسب‌وکار:
 * علی -> مدرسه A -> TEACHER
 * علی -> آکادمی B -> STUDENT
 * <p>
 * فیلد {@code active} دقیقاً برای این سناریو است: "مدرسه می‌خواهد دسترسی
 * یک دانش‌آموز خاص را غیرفعال کند" - بدون حذف کاربر، بدون حذف Membership،
 * فقط همین یک ردیف خاص (User+School+Role) غیرفعال می‌شود. سایر
 * Membershipهای همان کاربر (در همان مدرسه با نقش دیگر، یا در مدرسه دیگر)
 * تحت تأثیر قرار نمی‌گیرند.
 * <p>
 * توجه: schoolId در اینجا صرفاً یک شناسه عددی است (همان مدلی که در
 * TenantContext استفاده می‌شود)؛ ماژول iam مالک مدیریت خود مدرسه‌ها نیست
 * و فقط membership به آن‌ها را نگه می‌دارد.
 */
@Entity
@Table(
        name = "school_memberships",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "school_id", "role_id"})
)
@Getter
@Setter
@NoArgsConstructor
class SchoolMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(nullable = false)
    private boolean active = true;

    SchoolMembershipEntity(Long userId, Long schoolId, Long roleId) {
        this.userId = userId;
        this.schoolId = schoolId;
        this.roleId = roleId;
        this.active = true;
    }
}
