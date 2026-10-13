package com.example.roshdeandishe;

import com.example.roshdeandishe.platform.PlatformExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * فایلِ بزرگ‌تر از سقفِ multipart (۶ مگابایت) یک 4xx با پیامِ فارسی است، نه «خطای غیرمنتظره»ی ۵۰۰ (عکسِ گوشی برای
 * سؤال‌های فقط‌تصویر معمولاً بزرگ است).
 */
class PlatformUploadTooLargeTest {

    @Test
    void anOversizeUploadIsA413WithAPersianMessageNotA500() {
        ProblemDetail problem = new PlatformExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(6L * 1024 * 1024));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getType().toString()).endsWith("upload-too-large");
        assertThat(problem.getDetail()).contains("مگابایت");
    }
}
