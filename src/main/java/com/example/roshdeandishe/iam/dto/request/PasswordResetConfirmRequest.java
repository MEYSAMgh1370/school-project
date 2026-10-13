package com.example.roshdeandishe.iam.dto.request;

public record PasswordResetConfirmRequest(String token, String newPassword) {
}
