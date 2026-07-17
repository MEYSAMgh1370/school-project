package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicInfo;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.IntervalInfo;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * «برنامه‌ی مطالعه‌ی من» - همان مدل اعتماد
 * {@code academic.MyEnrollmentController}/{@code exam.MyExamsController}:
 * عمداً هیچ {@code @RequiresPermission}/{@code @RequiresModule} ندارد، چون
 * داده فقط درباره‌ی خودِ دانش‌آموزِ احرازشده است - studentId از
 * {@code AcademicFacade.findMyEnrollment} resolve می‌شود، هرگز از ورودی
 * کاربر. در ماژول studyplan (نه iam) به همان دلیل MyEnrollmentController:
 * iam نمی‌تواند به academic وابسته شود؛ studyplan از قبل هم به
 * AcademicFacade و هم CurriculumFacade وابسته است (StudyPlanFacadeImpl)، پس
 * این کنترلر هیچ وابستگی ماژولی تازه‌ای اضافه نمی‌کند.
 * <p>
 * برخلاف {@code StudyIntervalController} اداری (پشت STUDYPLAN_MANAGE، برای
 * مشاور/معلم که مجاز است بازه‌ی هر دانش‌آموزی را ویرایش کند)، ویرایش اینجا
 * از طریق {@link StudyPlanFacade#updateOwnInterval} می‌رود که مالکیتِ
 * دانش‌آموز روی همان بازه را هم چک می‌کند - بدون آن، حدس‌زدن intervalId
 * می‌توانست بازه‌ی دانش‌آموز دیگری را ویرایش کند.
 * <p>
 * {@code topicName} روی پاسخ embed می‌شود چون Curriculum پشت
 * CURRICULUM_VIEW/CURRICULUM_MANAGE است (فقط SCHOOL_ADMIN/TEACHER - بانک
 * سؤال شامل پاسخ صحیح است) و دانش‌آموز اجازه‌ی خواندن مستقیم آن
 * endpointها را ندارد؛ نام سرفصل اینجا سمت سرور (نه با یک فراخوانی جدا از
 * کلاینت) از CurriculumFacade.listTopics ترکیب می‌شود.
 */
@RestController
@RequestMapping("/api/me/schools/{schoolId}/study-intervals")
public class MyStudyIntervalController {

    private final StudyPlanFacade studyPlanFacade;
    private final AcademicFacade academicFacade;
    private final CurriculumFacade curriculumFacade;
    private final IamFacade iamFacade;

    public MyStudyIntervalController(StudyPlanFacade studyPlanFacade, AcademicFacade academicFacade,
                                     CurriculumFacade curriculumFacade, IamFacade iamFacade) {
        this.studyPlanFacade = studyPlanFacade;
        this.academicFacade = academicFacade;
        this.curriculumFacade = curriculumFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping
    public List<MyIntervalResponse> getWeek(@PathVariable Long schoolId, @RequestParam(required = false) LocalDate date) {
        Long userId = iamFacade.currentUserId().value();
        Long studentId = resolveStudentId(userId, schoolId);
        if (studentId == null) {
            return List.of();
        }
        List<IntervalInfo> intervals = studyPlanFacade.getWeek(schoolId, studentId, date);
        Map<Long, String> topicNames = topicNames(schoolId);
        return intervals.stream().map(info -> toResponse(info, topicNames)).toList();
    }

    @PatchMapping("/{intervalId}")
    public MyIntervalResponse updateInterval(@PathVariable Long schoolId, @PathVariable Long intervalId,
                                             @RequestBody UpdateMyIntervalRequest request) {
        Long userId = iamFacade.currentUserId().value();
        Long studentId = requireStudentId(userId, schoolId);
        IntervalInfo info = studyPlanFacade.updateOwnInterval(schoolId, studentId, intervalId, request.done(), request.testCount());
        return toResponse(info, topicNames(schoolId));
    }

    private Long resolveStudentId(Long userId, Long schoolId) {
        return academicFacade.findMyEnrollment(userId, schoolId)
                .map(enrollment -> enrollment.studentId().value())
                .orElse(null);
    }

    private Long requireStudentId(Long userId, Long schoolId) {
        Long studentId = resolveStudentId(userId, schoolId);
        if (studentId == null) {
            throw new StudyPlanException(new StudyPlanError.InvalidInterval("شما در این مدرسه دانش‌آموز نیستید."));
        }
        return studentId;
    }

    private Map<Long, String> topicNames(Long schoolId) {
        return curriculumFacade.listTopics(schoolId).stream()
                .collect(Collectors.toMap(t -> t.id().value(), TopicInfo::name, (a, b) -> a));
    }

    private static MyIntervalResponse toResponse(IntervalInfo info, Map<Long, String> topicNames) {
        String topicName = topicNames.getOrDefault(info.topicId(), "سرفصل #" + info.topicId());
        return new MyIntervalResponse(info.id().value(), info.topicId(), topicName, info.start(), info.end(),
                info.done(), info.testCount());
    }

    public record UpdateMyIntervalRequest(Boolean done, Integer testCount) {
    }

    public record MyIntervalResponse(Long id, Long topicId, String topicName, LocalDateTime start, LocalDateTime end,
                                     boolean done, int testCount) {
    }
}
