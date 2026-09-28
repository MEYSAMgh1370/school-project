package com.example.roshdeandishe.iam.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
