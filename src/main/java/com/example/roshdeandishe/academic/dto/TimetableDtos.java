package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade.ActivityInfo;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;
import com.example.roshdeandishe.academic.TimetableFacade.PeriodInfo;

import java.util.List;

/**
 * قراردادِ JSON برنامهٔ کلاسی. ساعت‌ها رشته‌ی «HH:mm» هستند (نه LocalTime) تا شکل
 * JSON به تنظیمات Jackson بستگی نداشته باشد و ورودی با پیام روشن رد شود.
 */
public final class TimetableDtos {

    private TimetableDtos() {
    }

    public record YearsResponse(List<String> years, String currentYear) {
    }

    public record PeriodRequest(String academicYear, String label, String startTime, String endTime) {
    }

    public record PeriodResponse(Long id, String academicYear, int position, String label,
                                 String startTime, String endTime) {
        public static PeriodResponse from(PeriodInfo p) {
            return new PeriodResponse(p.id(), p.academicYear(), p.position(), p.label(),
                    p.startTime().toString(), p.endTime().toString());
        }
    }

    public record ActivityRequest(String name) {
    }

    public record ActivityResponse(Long id, String name, boolean platformDefault) {
        public static ActivityResponse from(ActivityInfo a) {
            return new ActivityResponse(a.id(), a.name(), a.platformDefault());
        }
    }

    public record TeacherResponse(Long userId, String fullName) {
    }

    /**
     * {@code courseId} یا {@code activityId} - دقیقاً یکی. {@code weekday} رشته است (نه enum) تا
     * روزِ ناشناس مثل THU با پیام روشنِ 400 رد شود، نه خطای خواندنِ بدنه.
     */
    public record EntryRequest(Long periodId, String weekday, Long courseId, Long activityId,
                               Long teacherUserId) {
    }

    public record EntryResponse(Long id, Long classId, String className, Long periodId, TimetableDay weekday,
                                Long courseId, String courseName, Long activityId, String activityName,
                                Long teacherUserId, String teacherName) {
        public static EntryResponse from(EntryInfo e, String teacherName) {
            return new EntryResponse(e.id(), e.classId(), e.className(), e.periodId(), e.weekday(),
                    e.courseId(), e.courseName(), e.activityId(), e.activityName(),
                    e.teacherUserId(), teacherName);
        }
    }

    /** یک «دید» از برنامه برای صفحه‌ی من: کلاسِ خودم، کلاسِ یکی از فرزندان، یا ساعت‌های تدریسِ خودم. */
    public record MyTimetableView(String kind, Long studentId, String studentName, Long classId,
                                  String className, List<EntryResponse> entries) {
    }

    public record MyTimetableResponse(String academicYear, List<PeriodResponse> periods,
                                      List<MyTimetableView> views) {
    }
}
