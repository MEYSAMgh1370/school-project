package com.example.roshdeandishe.studyplan.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * (de)serialize کردن ستون‌های متنیِ JSON این ماژول (course_info،
 * default_schedule، current_schedule - رجوع به توضیح PlanEntity). یک
 * ObjectMapper ساده و خصوصی، جدا از هر Bean سراسری - چون تمام تایپ‌های
 * این ماژول فقط String/Long/double/int/List/Map هستند (زمان‌ها
 * به‌صورت رشته‌ی "HH:mm" نگه‌داری می‌شوند، نه LocalTime)، نیازی به
 * ماژول زمانِ Jackson نیست.
 */
final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("امکان تبدیل مقدار به JSON وجود نداشت.", e);
        }
    }

    static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("امکان خواندن مقدار JSON وجود نداشت.", e);
        }
    }
}
