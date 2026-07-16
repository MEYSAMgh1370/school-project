package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * «برنامه‌ی مطالعاتیِ خودِ من، در این مدرسه» - همان مدل اعتماد
 * {@code academic.MyEnrollmentController}/{@code finance.MyInvoicesController}:
 * عمداً بدون {@code @RequiresPermission}/{@code @RequiresModule}، چون داده
 * درباره‌ی خودِ کاربر احراز هویت‌شده است، نه یک قابلیت سطح مدرسه. هرگز
 * studentId دلخواه از ورودی نمی‌گیرد - همیشه از
 * {@code AcademicFacade.findMyEnrollment(userId, schoolId)} استخراج می‌شود.
 * <p>
 * چرا در ماژول studyplan (نه iam/academic): iam نمی‌تواند به academic یا
 * studyplan وابسته شود؛ این کنترلر IamFacade + AcademicFacade +
 * StudyPlanFacade خودش را ترکیب می‌کند، دقیقاً همان الگوی bale/
 * MyEnrollmentController برای «به این کاربرِ مشخص چه می‌توان گفت».
 */
@RestController
public class MyStudyWeekController {

    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;
    private final StudyPlanFacade studyPlanFacade;

    public MyStudyWeekController(IamFacade iamFacade, AcademicFacade academicFacade, StudyPlanFacade studyPlanFacade) {
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
        this.studyPlanFacade = studyPlanFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/study-schedule")
    public ResponseEntity<StudyIntervalController.ScheduleResponse> mySchedule(@PathVariable Long schoolId) {
        return myStudentId(schoolId)
                .map(studentId -> ResponseEntity.ok(
                        StudyIntervalController.toResponse(studyPlanFacade.getOrCreateSchedule(schoolId, studentId))
                ))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/me/schools/{schoolId}/study-week")
    public ResponseEntity<List<StudyIntervalController.StudyIntervalResponse>> myWeek(
            @PathVariable Long schoolId, @RequestParam LocalDate weekStartDate
    ) {
        return myStudentId(schoolId)
                .map(studentId -> ResponseEntity.ok(
                        studyPlanFacade.getWeek(schoolId, studentId, weekStartDate).stream()
                                .map(StudyIntervalController::toResponse)
                                .toList()
                ))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private java.util.Optional<Long> myStudentId(Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        return academicFacade.findMyEnrollment(userId, schoolId).map(e -> e.studentId().value());
    }
}
