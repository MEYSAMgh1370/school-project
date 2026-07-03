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
        };
    }
}
