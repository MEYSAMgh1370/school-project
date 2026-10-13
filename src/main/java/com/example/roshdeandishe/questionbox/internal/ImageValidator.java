package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.shared.QuestionBoxError;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxException;

/**
 * اعتبارسنجی تصویرِ آپلودی (سؤال و سربرگ): حجم و نوعِ واقعی از روی بایت‌های ابتدایی. نوعِ اعلامی کلاینت یا
 * پسوند فایل ملاک نیست؛ SVG/HTML عمداً مجاز نیست (اسکریپت‌پذیر). سقف‌ها فرضِ ثبت‌شده در برنامه‌اند.
 */
final class ImageValidator {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private ImageValidator() {
    }

    /** نوعِ MIME واقعیِ تصویر را برمی‌گرداند یا InvalidImage می‌اندازد. */
    static String validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("فایل تصویر خالی است.");
        }
        if (content.length > MAX_BYTES) {
            throw invalid("حجم تصویر نباید از " + (MAX_BYTES / (1024 * 1024)) + " مگابایت بیشتر باشد.");
        }
        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (content.length > 12 && startsWith(content, 'R', 'I', 'F', 'F')
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        throw invalid("فقط تصویر PNG، JPEG یا WebP مجاز است.");
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static QuestionBoxException invalid(String reason) {
        return new QuestionBoxException(new QuestionBoxError.InvalidImage(reason));
    }
}
