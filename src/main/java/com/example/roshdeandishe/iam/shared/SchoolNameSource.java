package com.example.roshdeandishe.iam.shared;

import java.util.Optional;

/**
 * نامِ مدرسه برای متنِ دعوت‌نامه و صفحه‌ی فعال‌سازی. {@code iam} عمداً به ماژول {@code school} وابسته
 * نیست (وابستگی iam→school حلقه می‌ساخت)، پس ماژول {@code school} این رابط را پیاده می‌کند و
 * {@code iam} فقط به همین رابطِ عمومی نگاه می‌کند. اگر پیاده‌سازی‌ای نباشد یا مدرسه پیدا نشود،
 * دعوت‌نامه بدونِ نامِ مدرسه ساخته می‌شود.
 */
public interface SchoolNameSource {

    Optional<String> nameOf(Long schoolId);
}
