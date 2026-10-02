package com.example.roshdeandishe.web.dto;

/**
 * نامِ یک فرزندِ لینک‌شده‌ی والد، برای صفحه‌هایی که فقط باید فرزندان را
 * بشناسند و نیازی به داده‌ی مالی ندارند («نمره‌های من»: عنوان آکاردئون).
 * API نمره‌ها نام دانش‌آموز را برنمی‌گرداند، پس نام از سمت سرور به صفحه می‌رسد.
 */
public record WardRef(Long studentId, Long schoolId, String name) {
}
