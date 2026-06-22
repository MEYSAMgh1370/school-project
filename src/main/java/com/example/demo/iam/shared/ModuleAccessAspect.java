package com.example.roshdeandishe.iam.shared;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * این Aspect قبل از اجرای هر متد (یا متدهای داخل کلاسی) که با
 * {@link RequiresModule} علامت‌گذاری شده، از طریق {@link IamFacade#checkAccess}
 * بررسی می‌کند آیا کاربر فعلی به آن ماژول دسترسی دارد یا نه.
 * <p>
 * توجه به تغییر کلیدی نسبت به نسخه‌ی قبلی: دیگر فرض نمی‌شود context همیشه
 * مدرسه‌محور است. اگر {@link TenantContext} مقداردهی شده باشد (هدر
 * X-School-ID ارسال شده)، یک {@code SchoolContext} ساخته می‌شود؛ در غیر
 * این صورت یک {@code PlatformContext} ساخته می‌شود که برای ماژول‌های
 * USER-scoped یا PLATFORM-scoped کاملاً معتبر است (مثلاً یک درخواست به
 * AI_TUTOR که اصلاً به مدرسه‌ای مرتبط نیست).
 */
@Aspect
@Component
@Order(10) // بعد از فیلترهای امنیتی، قبل از منطق اصلی متد
public class ModuleAccessAspect {

    private final IamFacade iamFacade;

    public ModuleAccessAspect(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    /**
     * حالت اول: انوتیشن مستقیماً روی متد قرار گرفته باشد.
     * توجه: ترکیب @annotation و @within در یک pointcut واحد با binding مشترک
     * در AspectJ به‌درستی resolve نمی‌شود، به همین دلیل دو متد advice جدا
     * تعریف شده است (هر دو همان منطق checkModuleAccess را صدا می‌زنند).
     */
    @Before("@annotation(requiresModule)")
    public void checkMethodLevelAccess(RequiresModule requiresModule) {
        checkModuleAccess(requiresModule);
    }

    /**
     * حالت دوم: انوتیشن روی کلاس (سطح Type) قرار گرفته باشد، یعنی همه‌ی
     * متدهای آن کلاس مشروط به فعال بودن ماژول هستند.
     */
    @Before("@within(requiresModule) && !@annotation(com.example.roshdeandishe.iam.shared.RequiresModule)")
    public void checkClassLevelAccess(RequiresModule requiresModule) {
        checkModuleAccess(requiresModule);
    }

    private void checkModuleAccess(RequiresModule requiresModule) {
        IamFacade.UserId userId = iamFacade.currentUserId();
        IamFacade.ModuleCode moduleCode = new IamFacade.ModuleCode(requiresModule.value());

        AccessContext context = TenantContext.isSet()
                ? new AccessContext.SchoolContext(TenantContext.get())
                : new AccessContext.PlatformContext();

        AccessDecision decision = iamFacade.checkAccess(userId, moduleCode, context);

        if (decision instanceof AccessDecision.Denied denied) {
            Long schoolId = context instanceof AccessContext.SchoolContext schoolContext
                    ? schoolContext.schoolId()
                    : null;
            throw new IamException(
                    new IamError.ModuleNotEntitled(moduleCode.value(), schoolId, denied.reason())
            );
        }
    }
}
