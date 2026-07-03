package com.example.roshdeandishe.iam.shared;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * عمداً {@code @Order(11)} است - یعنی بعد از {@link ModuleAccessAspect}
 * (Order 10) اجرا می‌شود. ترتیب درست است: اول بررسی شود مدرسه اصلاً ماژول
 * را خریده یا نه (ارزان‌تر و عمومی‌تر)، بعد اگر گذشت، بررسی دقیق‌تر شود
 * که آیا نقش فعال *این* کاربر در *این* مدرسه چنین Permission‌ای دارد یا نه.
 */
@Aspect
@Component
@Order(11)
public class PermissionAccessAspect {

    private final IamFacade iamFacade;

    public PermissionAccessAspect(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @Before("@annotation(requiresPermission)")
    public void checkMethodLevelAccess(RequiresPermission requiresPermission) {
        checkPermissionAccess(requiresPermission);
    }

    @Before("@within(requiresPermission) && !@annotation(com.example.roshdeandishe.iam.shared.RequiresPermission)")
    public void checkClassLevelAccess(RequiresPermission requiresPermission) {
        checkPermissionAccess(requiresPermission);
    }

    private void checkPermissionAccess(RequiresPermission requiresPermission) {
        IamFacade.UserId userId = iamFacade.currentUserId();
        IamFacade.PermissionCode permissionCode = new IamFacade.PermissionCode(requiresPermission.value());

        AccessContext context = TenantContext.isSet()
                ? new AccessContext.SchoolContext(TenantContext.get())
                : new AccessContext.PlatformContext();

        AccessDecision decision = iamFacade.checkPermission(userId, permissionCode, context);

        if (decision instanceof AccessDecision.Denied denied) {
            Long schoolId = context instanceof AccessContext.SchoolContext schoolContext
                    ? schoolContext.schoolId()
                    : null;
            throw new IamException(
                    new IamError.PermissionDenied(permissionCode.value(), schoolId, denied.reason())
            );
        }
    }
}
