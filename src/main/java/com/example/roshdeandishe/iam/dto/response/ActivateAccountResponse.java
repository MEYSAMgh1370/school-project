package com.example.roshdeandishe.iam.dto.response;

/** {@code email} حسابِ فعال‌شده است؛ صفحه‌ی فعال‌سازی با آن و همان رمز، کاربر را وارد می‌کند. */
public record ActivateAccountResponse(String message, String email) {
}
