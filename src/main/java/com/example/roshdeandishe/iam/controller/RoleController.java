package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.RoleInfo;
import com.example.roshdeandishe.iam.dto.CreateRoleRequest;
import com.example.roshdeandishe.iam.dto.PermissionResponse;
import com.example.roshdeandishe.iam.dto.RoleResponse;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * مدیریت نقش‌های سفارشی یک مدرسه - حل‌کننده‌ی Open Question 3 در CLAUDE.md
 * («آیا یک مدرسه می‌تواند نقش سفارشی تعریف کند؟»). {@code ROLE_MANAGE} تنها
 * برای {@code SCHOOL_ADMIN} seed شده - همان سطح حساسیتِ {@code MEMBERSHIP_MANAGE}
 * (کسی که می‌تواند سطح دسترسی بسازد، باید همان ادمین کامل مدرسه باشد).
 * <p>
 * نقش‌های سیستمی (STUDENT/TEACHER/PARENT/SCHOOL_ADMIN/FINANCE_ADMIN) از این
 * مسیر قابل‌ویرایش نیستند - فقط نقش تازه ساخته می‌شود (تصمیم صریح CLAUDE.md
 * §11 Q3). بدون {@code @RequiresModule}: مثل MembershipController/
 * SchoolController، مدیریت نقش یک قابلیت پلتفرمی است، نه یک ماژول قابل‌اشتراک.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
public class RoleController {

    private final IamFacade iamFacade;

    public RoleController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping("/permissions")
    @RequiresPermission("ROLE_MANAGE")
    public List<PermissionResponse> listPermissions(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return iamFacade.listPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @GetMapping("/roles")
    @RequiresPermission("ROLE_MANAGE")
    public List<RoleResponse> listRoles(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return iamFacade.listRoles(schoolId).stream()
                .map(RoleResponse::from)
                .toList();
    }

    @PostMapping("/roles")
    @RequiresPermission("ROLE_MANAGE")
    public RoleResponse createRole(@PathVariable Long schoolId, @RequestBody CreateRoleRequest request) {
        TenantContext.requireMatches(schoolId);

        RoleInfo info = iamFacade.createCustomRole(
                schoolId, request.code(), request.displayName(), request.permissionCodes());
        return RoleResponse.from(info);
    }
}
