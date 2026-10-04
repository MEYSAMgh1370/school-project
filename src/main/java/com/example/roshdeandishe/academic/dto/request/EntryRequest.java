package com.example.roshdeandishe.academic.dto.request;

/**
 * {@code courseId} یا {@code activityId} - دقیقاً یکی. {@code weekday} رشته است (نه enum) تا
 * روزِ ناشناس مثل THU با پیام روشنِ 400 رد شود، نه خطای خواندنِ بدنه.
 */
public record EntryRequest(Long periodId, String weekday, Long courseId, Long activityId,
                           Long teacherUserId) {
}
