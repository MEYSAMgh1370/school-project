package com.example.roshdeandishe.iam;

import com.example.roshdeandishe.iam.IamFacade.PermissionInfo;
import com.example.roshdeandishe.iam.IamFacade.RoleInfo;
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

    public record PermissionResponse(Long id, String code, String displayName) {
        static PermissionResponse from(PermissionInfo info) {
            return new PermissionResponse(info.id(), info.code(), info.displayName());
        }
    }

    public record RoleResponse(Long id, String code, String displayName, boolean custom, List<String> permissionCodes) {
        static RoleResponse from(RoleInfo info) {
            return new RoleResponse(info.id(), info.code(), info.displayName(), info.schoolId() != null, info.permissionCodes());
        }
    }

    public record CreateRoleRequest(String code, String displayName, List<String> permissionCodes) {
    }
}
