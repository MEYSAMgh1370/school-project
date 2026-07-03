package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * هر ماژول قابل‌فعال‌سازی در پلتفرم (Academic, Finance, AI_Tutor, ...) یک
 * ردیف در این جدول است. فیلد {@code scope} مشخص می‌کند تخصیص دسترسی این
 * ماژول باید بر اساس مدرسه باشد یا بر اساس کاربر مستقل، یا اصلاً برای همه
 * در سطح پلتفرم باز باشد.
 * <p>
 * این فیلد همان چیزی است که {@link AccessDecisionService} برای انتخاب
 * منبع Entitlement درست (School یا User) از آن استفاده می‌کند.
 */
@Entity
@Table(name = "modules", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class ModuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModuleScope scope;

    ModuleEntity(String code, String displayName, ModuleScope scope) {
        this.code = code;
        this.displayName = displayName;
        this.scope = scope;
    }

    /**
     * - PLATFORM: همیشه برای همه باز است (نیاز به Entitlement خاصی ندارد).
     * - SCHOOL: دسترسی بر اساس اشتراک مدرسه ({@code SchoolEntitlementEntity}).
     * - USER: دسترسی بر اساس اشتراک شخصی کاربر ({@code UserEntitlementEntity}) -
     *   مستقل از اینکه آن کاربر عضو کدام مدرسه باشد.
     */
    enum ModuleScope {
        PLATFORM, SCHOOL, USER
    }
}
