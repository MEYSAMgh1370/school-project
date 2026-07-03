package com.example.roshdeandishe.iam.shared.exception;

public final class IamException extends RuntimeException {

    private final IamError error;

    public IamException(IamError error) {
        super(describe(error));
        this.error = error;
    }

    public IamError error() {
        return error;
    }

    private static String describe(IamError error) {
        return switch (error) {
            case IamError.ModuleNotEntitled e ->
                    "ماژول '" + e.moduleCode() + "' برای context داده‌شده فعال نیست: " + e.reason();
            case IamError.ModuleNotFound e ->
                    "ماژول '" + e.moduleCode() + "' در سیستم تعریف نشده است.";
            case IamError.PermissionDenied e ->
                    "Permission '" + e.permissionCode() + "' برای کاربر در این context مجاز نیست: " + e.reason();
            case IamError.PermissionNotFound e ->
                    "Permission '" + e.permissionCode() + "' در سیستم تعریف نشده است.";
            case IamError.MembershipNotFound e ->
                    "کاربر " + e.userId() + " عضویتی در مدرسه " + e.schoolId() + " ندارد.";
            case IamError.MembershipIdNotFound e ->
                    "عضویتی با شناسه " + e.membershipId() + " یافت نشد.";
            case IamError.RoleNotFound e ->
                    "نقشی با کد '" + e.roleCode() + "' در سیستم تعریف نشده است.";
            case IamError.ModuleScopeMismatch e ->
                    "ماژول '" + e.moduleCode() + "' از نوع " + e.actualScope() +
                    " است، نه " + e.expectedScope() + " - عملیات نامعتبر است.";
            case IamError.UserNotFound e ->
                    "کاربر یافت نشد" + (e.userId() != null ? " (شناسه: " + e.userId() + ")" : ".");
        };
    }
}
