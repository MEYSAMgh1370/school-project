package com.example.roshdeandishe.notification.internal;

import java.util.Optional;

/**
 * کدگذاریِ callback_data به شکل {@code "<code>:<id>"} (مثلاً {@code g:123}). بله هر callback_data را
 * حداکثر ۶۴ بایت می‌پذیرد؛ کوتاه‌ترین شکل همیشه زیر آن می‌ماند. رمزگشایی هر ورودیِ ناقص یا کدِ ناشناخته را
 * خالی برمی‌گرداند؛ اعتبارِ id را فراخواننده با {@link SubjectResolver} می‌سنجد.
 */
final class CallbackCodec {

    private CallbackCodec() {
    }

    /** نتیجه‌ی رمزگشایی؛ id هنوز بررسی‌نشده است. */
    record Decoded(BotIntent intent, long id) {
    }

    static String encode(BotIntent intent, BotTarget target) {
        return intent.code() + ":" + target.id();
    }

    static Optional<Decoded> decode(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int colon = data.indexOf(':');
        if (colon <= 0 || colon == data.length() - 1) {
            return Optional.empty();
        }
        String idPart = data.substring(colon + 1);
        for (int i = 0; i < idPart.length(); i++) {
            char c = idPart.charAt(i);
            if (c < '0' || c > '9') {
                return Optional.empty();
            }
        }
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return BotIntent.byCode(data.substring(0, colon)).map(intent -> new Decoded(intent, id));
    }
}
