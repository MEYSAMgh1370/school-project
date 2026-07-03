package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * جدول واسط بین Role و Permission. هر ردیف یعنی "این Role، این Permission
 * را دارد". اضافه کردن یک سطح دسترسی جدید (مثلاً یک نوع Admin محدودتر)
 * یعنی فقط چند ردیف داده جدید، نه تغییر کد.
 */
@Entity
@Table(
        name = "role_permissions",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"})
)
@Getter
@Setter
@NoArgsConstructor
class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    RolePermissionEntity(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }
}
