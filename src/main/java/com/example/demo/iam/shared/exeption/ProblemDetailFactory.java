package com.example.roshdeandishe.iam.shared.exeption;

import com.example.roshdeandishe.iam.shared.exeption.ExceptionDescriptor;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ProblemDetailFactory {

    public ProblemDetail create(Exception ex, ExceptionDescriptor descriptor) {

        ProblemDetail problem = ProblemDetail.forStatus(descriptor.status());

        problem.setTitle(descriptor.status().getReasonPhrase());
        problem.setStatus(descriptor.status().value());

        problem.setProperty("code", descriptor.code());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("message", ex.getMessage() != null
                ? ex.getMessage()
                : descriptor.defaultMessage());

        return problem;
    }
}