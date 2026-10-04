package com.example.roshdeandishe.iam.dto.response;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
