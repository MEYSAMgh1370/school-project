package com.example.roshdeandishe.web.model;

/**
 * یک گامِ چک‌لیستِ «راه‌اندازی مدرسه» روی پیشخوانِ مدیر. {@code done} از داده‌ی واقعیِ مدرسه می‌آید، نه از
 * کلیکِ مدیر. {@code href} صفحه‌ای است که همان گام را انجام می‌دهد.
 */
public record SetupStep(String key, String label, String hint, String href, boolean done) {
}
