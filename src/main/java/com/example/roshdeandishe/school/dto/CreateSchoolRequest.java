package com.example.roshdeandishe.school.dto;

import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;

/**
 * {@code weekStart} اختیاری است: خالی = SATURDAY (هفته‌ی ایرانی، همان رفتار پیش از W9).
 */
public record CreateSchoolRequest(String name, String timezone, String locale, SchoolLevel level,
                                  String slug, WeekStart weekStart) {
}
