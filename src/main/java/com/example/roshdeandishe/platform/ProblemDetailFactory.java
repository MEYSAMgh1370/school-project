package com.example.roshdeandishe.platform;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/**
 * تنها مسئولیت این کلاس، ساخت یکدست {@link ProblemDetail} (RFC 7807) است.
 * هیچ دانشی از Exceptionهای دامنه‌ای هیچ ماژولی ندارد - هر ماژول
 * (iam, academic, finance, ...) این Factory را برای ساخت پاسخ خطای خودش
 * صدا می‌زند، بدون اینکه ماژول‌ها به هم وابسته شوند.
 */
public final class ProblemDetailFactory {

    private static final String ERROR_BASE_URI = "https://roshdeandishe.example.com/errors/";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail problem(HttpStatus status, String typeSlug, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(ERROR_BASE_URI + typeSlug));
        return problemDetail;
    }
}
