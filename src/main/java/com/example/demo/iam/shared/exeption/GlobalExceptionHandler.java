package com.example.roshdeandishe.iam.shared.exeption;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExceptionRegistry registry;
    private final ProblemDetailFactory factory;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex) {

        ExceptionDescriptor descriptor = registry.resolve(ex);
        ProblemDetail problem = factory.create(ex, descriptor);

        return ResponseEntity
                .status(descriptor.status())
                .body(problem);
    }
}