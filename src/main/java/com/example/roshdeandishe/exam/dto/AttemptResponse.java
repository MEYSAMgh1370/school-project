package com.example.roshdeandishe.exam.dto;

import java.time.Instant;

public record AttemptResponse(String token, Instant expireAt) {
}
