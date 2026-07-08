package com.example.roshdeandishe.iam.shared.exception;

/**
 * مدل خطای دامنه‌محور ماژول iam. هر حالت یک {@code record} با داده‌ی دقیق
 * لازم برای ساخت پاسخ {@link org.springframework.http.ProblemDetail} است.
 * <p>
 * چون sealed است، {@code IamProblemResolver} و {@code IamException} با
 * switch expression بدون default-branch همه‌ی حالت‌ها را پوشش می‌دهند -
 * اضافه شدن PermissionNotFound/PermissionDenied/MembershipIdNotFound در
 * این فاز، دقیقاً همان لحظه‌ای بود که این exhaustiveness خودش را نشان داد:
 * کامپایلر هر دو جای دیگر را به خطا داد تا حتماً پوشش داده شوند.
 */
public sealed interface IamError {

    record ModuleNotEntitled(String moduleCode, Long schoolId, String reason) implements IamError {
    }

    record ModuleNotFound(String moduleCode) implements IamError {
    }

    record PermissionDenied(String permissionCode, Long schoolId, String reason) implements IamError {
    }

    record PermissionNotFound(String permissionCode) implements IamError {
    }

    record MembershipNotFound(Long userId, Long schoolId) implements IamError {
    }

    record MembershipIdNotFound(Long membershipId) implements IamError {
    }

    record RoleNotFound(String roleCode) implements IamError {
    }

    record ModuleScopeMismatch(String moduleCode, String expectedScope, String actualScope) implements IamError {
    }

    record UserNotFound(Long userId) implements IamError {
    }

    record InviteTokenNotFound(String token) implements IamError {
    }

    record InviteTokenExpired(String token) implements IamError {
    }

    record InviteTokenAlreadyRedeemed(String token) implements IamError {
    }

    record InvalidPassword(String reason) implements IamError {
    }

    record InvalidRegistration(String reason) implements IamError {
    }

    record EmailAlreadyRegistered(String email) implements IamError {
    }

    record InvalidPhoneNumber(String reason) implements IamError {
    }

    record PhoneNumberAlreadyClaimed(String phoneNumber) implements IamError {
    }

    record InvalidCredentials(String reason) implements IamError {
    }
}
