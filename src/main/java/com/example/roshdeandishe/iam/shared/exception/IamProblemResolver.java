package com.example.roshdeandishe.iam.shared.exception;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class IamProblemResolver {

    public ProblemDetail resolve(IamError error) {
        return switch (error) {
            case IamError.ModuleNotEntitled e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "module-not-entitled",
                    "ماژول فعال نیست", e.reason()
            );
            case IamError.ModuleNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.INTERNAL_SERVER_ERROR, "module-not-found",
                    "ماژول تعریف نشده",
                    "ماژول '" + e.moduleCode() + "' در جدول iam.modules ثبت نشده است."
            );
            case IamError.PermissionDenied e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "permission-denied",
                    "دسترسی کافی نیست", e.reason()
            );
            case IamError.PermissionNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.INTERNAL_SERVER_ERROR, "permission-not-found",
                    "Permission تعریف نشده",
                    "Permission '" + e.permissionCode() + "' در جدول iam.permissions ثبت نشده است."
            );
            case IamError.MembershipNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "membership-not-found",
                    "عضویت یافت نشد",
                    "کاربر " + e.userId() + " عضویتی در مدرسه " + e.schoolId() + " ندارد."
            );
            case IamError.MembershipIdNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "membership-id-not-found",
                    "عضویت یافت نشد",
                    "عضویتی با شناسه " + e.membershipId() + " یافت نشد."
            );
            case IamError.RoleNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "role-not-found",
                    "نقش نامعتبر",
                    "نقشی با کد '" + e.roleCode() + "' در سیستم تعریف نشده است."
            );
            case IamError.ModuleScopeMismatch e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "module-scope-mismatch",
                    "نوع ماژول نامعتبر",
                    "ماژول '" + e.moduleCode() + "' از نوع " + e.actualScope() +
                    " است، نه " + e.expectedScope() + "."
            );
            case IamError.UserNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.UNAUTHORIZED, "user-not-found",
                    "کاربر یافت نشد",
                    "کاربر احراز هویت‌شده‌ی فعلی در سیستم پیدا نشد."
            );
            case IamError.InviteTokenNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "invite-token-not-found",
                    "توکن دعوت یافت نشد",
                    "توکن دعوت نامعتبر است یا وجود ندارد."
            );
            case IamError.InviteTokenExpired e -> ProblemDetailFactory.problem(
                    HttpStatus.GONE, "invite-token-expired",
                    "توکن دعوت منقضی شده",
                    "این توکن دعوت منقضی شده است. یک توکن جدید درخواست کنید."
            );
            case IamError.InviteTokenAlreadyRedeemed e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "invite-token-already-redeemed",
                    "توکن دعوت قبلاً استفاده شده",
                    "این توکن دعوت قبلاً برای فعال‌سازی حساب استفاده شده است."
            );
            case IamError.InvalidPassword e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-password",
                    "رمز عبور نامعتبر",
                    e.reason()
            );
            case IamError.InvalidRegistration e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-registration",
                    "اطلاعات ثبت‌نام نامعتبر",
                    e.reason()
            );
            case IamError.EmailAlreadyRegistered e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "email-already-registered",
                    "ایمیل قبلاً ثبت شده",
                    "کاربری با ایمیل '" + e.email() + "' از قبل وجود دارد. اگر این حساب شماست، " +
                    "وارد شوید یا (اگر دعوت‌نامه دارید) حساب را از مسیر فعال‌سازی فعال کنید."
            );
            case IamError.InvalidPhoneNumber e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-phone-number",
                    "شماره تلفن نامعتبر", e.reason()
            );
            case IamError.PhoneNumberAlreadyClaimed e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "phone-number-already-claimed",
                    "شماره تلفن قبلاً ثبت شده",
                    "شماره '" + e.phoneNumber() + "' قبلاً برای حساب کاربری دیگری ثبت شده است."
            );
            case IamError.InvalidCredentials e -> ProblemDetailFactory.problem(
                    HttpStatus.UNAUTHORIZED, "invalid-credentials",
                    "ورود ناموفق", e.reason()
            );
            case IamError.InvalidRoleDefinition e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-role-definition",
                    "تعریف نقش نامعتبر", e.reason()
            );
            case IamError.InvalidPermissionCode e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-permission-code",
                    "Permission نامعتبر",
                    "Permission ای با کد '" + e.permissionCode() + "' در سیستم تعریف نشده است."
            );
            case IamError.RoleCodeAlreadyExists e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "role-code-already-exists",
                    "کد نقش تکراری است",
                    "کد '" + e.code() + "' یا با یک نقش سیستمی یکسان است یا قبلاً برای این مدرسه ساخته شده است."
            );
        };
    }
}
