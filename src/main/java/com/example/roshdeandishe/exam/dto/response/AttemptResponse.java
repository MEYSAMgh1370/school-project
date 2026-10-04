package com.example.roshdeandishe.exam.dto.response;

import java.time.Instant;

public record AttemptResponse(String token, Instant expireAt) {
}
