package com.example.roshdeandishe.iam.shared.exeption;

import org.springframework.http.HttpStatus;

public record ExceptionDescriptor(
        HttpStatus status,
        String code,
        String defaultMessage
) {}
