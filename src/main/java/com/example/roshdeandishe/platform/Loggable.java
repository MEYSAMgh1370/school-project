package com.example.roshdeandishe.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * با قرار گرفتن روی یک متد یا کلاس، {@link LoggingAspect} ورود/خروج و
 * مدت‌زمان اجرای آن را لاگ می‌کند. عمداً opt-in است (نه لاگ‌گیری خودکار
 * روی همه‌ی متدها) تا لاگ‌ها شلوغ و بی‌فایده نشوند - فقط جاهایی که واقعاً
 * مهم است (مثل متدهای عمومی Facadeها) این انوتیشن را می‌گیرند.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Loggable {
}
