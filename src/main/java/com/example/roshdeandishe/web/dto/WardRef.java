package com.example.roshdeandishe.web.dto;

/**
 * یک دانش‌آموزِ قابل‌انتخاب در «نمره‌های من»: فرزندِ لینک‌شده‌ی والد، یا خودِ
 * کاربر وقتی دانش‌آموزِ همان مدرسه است ({@code own}). API نمره‌ها فقط ردیف
 * نمره برمی‌گرداند (نه نام و نه ثبت‌نام)، پس نام و هویتِ دانش‌آموزِ خودِ کاربر
 * از سمت سرور به صفحه می‌رسد - وگرنه دانش‌آموزِ بدون نمره در فهرست نمی‌آمد.
 * بدون داده‌ی مالی.
 */
public record WardRef(Long studentId, Long schoolId, String name, boolean own) {

    public WardRef(Long studentId, Long schoolId, String name) {
        this(studentId, schoolId, name, false);
    }
}
