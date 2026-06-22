package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ریشه‌ی هویت در سیستم. برخلاف مدل قبلی، Teacher/Student/Parent/SchoolAdmin
 * دیگر نوع جدا ندارند - همه یک {@code UserEntity} هستند که نقش‌هایش از طریق
 * {@link SchoolMembershipEntity} (برای هر مدرسه به‌صورت جدا) تعیین می‌شود.
 * <p>
 * این Entity عمداً هیچ ارجاعی به schoolId یا role ندارد؛ یک کاربر می‌تواند
 * هم‌زمان در چند مدرسه با نقش‌های متفاوت عضو باشد.
 */
@Entity
@Table(name = "users", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.ACTIVE;

    UserEntity(String email, String fullName) {
        this.email = email;
        this.fullName = fullName;
        this.status = UserStatus.ACTIVE;
    }

    enum UserStatus {
        ACTIVE, SUSPENDED
    }
}
