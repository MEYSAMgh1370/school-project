package com.example.roshdeandishe.finance.dto.response;

import java.time.Instant;

public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes,
                                 Instant uploadedAt) {
}
