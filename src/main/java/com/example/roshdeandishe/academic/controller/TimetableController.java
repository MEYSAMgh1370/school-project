package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.academic.dto.ClassResponse;
import com.example.roshdeandishe.academic.dto.CourseResponse;
import com.example.roshdeandishe.academic.dto.request.*;
import com.example.roshdeandishe.academic.dto.response.*;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * برنامهٔ کلاسی، سمتِ مدیر (openspec/changes/class-timetable.md): زنگ‌ها،
 * فعالیت‌های فوق‌برنامه و خانه‌های هر کلاس. همه‌ی مسیرها با ماژول ACADEMIC و
 * مجوزِ جدید {@code TIMETABLE_MANAGE} گیت می‌شوند، از جمله فهرستِ سال‌ها، معلم‌ها و
 * درس‌ها که صفحه برای ساختنِ انتخابگرها لازم دارد - تا همین یک مجوز برای کارِ مدیر
 * کافی باشد، نه ترکیبی از CLASS_VIEW/GRADES_EDIT و غیره.
 * <p>
 * خواندنِ برنامه‌ی خودِ کاربر مجوز نمی‌خواهد و در {@link MyTimetableController} است.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/timetable")
@RequiresModule("ACADEMIC")
@RequiresPermission("TIMETABLE_MANAGE")
public class TimetableController {

    private final TimetableFacade timetableFacade;
    private final AcademicFacade academicFacade;
    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;
    private final AcademicYearVisibility yearVisibility;

    public TimetableController(TimetableFacade timetableFacade, AcademicFacade academicFacade,
                               SchoolFacade schoolFacade, IamFacade iamFacade, AcademicYearVisibility yearVisibility) {
        this.timetableFacade = timetableFacade;
        this.academicFacade = academicFacade;
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
        this.yearVisibility = yearVisibility;
    }

    /** مدیر هر سالِ فهرست را می‌تواند باز کند؛ سالِ جاریِ مدرسه پیش‌فرضِ صفحه است. */
    @GetMapping("/years")
    public YearsResponse years(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        List<String> codes = schoolFacade.listAcademicYears().stream().map(AcademicYearInfo::code).toList();
        String current = schoolFacade.findCurrentAcademicYear(new SchoolFacade.SchoolId(schoolId))
                .map(AcademicYearInfo::code).orElse(null);
        return new YearsResponse(codes, current);
    }

    // ---- زنگ‌ها ----

    @GetMapping("/periods")
    public List<PeriodResponse> periods(@PathVariable Long schoolId, @RequestParam String academicYear) {
        TenantContext.requireMatches(schoolId);

        return timetableFacade.listPeriods(schoolId, academicYear).stream().map(PeriodResponse::from).toList();
    }

    @PostMapping("/periods")
    public PeriodResponse createPeriod(@PathVariable Long schoolId, @RequestBody PeriodRequest request) {
        TenantContext.requireMatches(schoolId);

        return PeriodResponse.from(timetableFacade.createPeriod(schoolId, request.academicYear(), request.label(),
                parseTime(request.startTime()), parseTime(request.endTime())));
    }

    @PutMapping("/periods/{periodId}")
    public PeriodResponse updatePeriod(@PathVariable Long schoolId, @PathVariable Long periodId,
                                       @RequestBody PeriodRequest request) {
        TenantContext.requireMatches(schoolId);

        return PeriodResponse.from(timetableFacade.updatePeriod(schoolId, periodId, request.label(),
                parseTime(request.startTime()), parseTime(request.endTime())));
    }

    @DeleteMapping("/periods/{periodId}")
    public ResponseEntity<Void> deletePeriod(@PathVariable Long schoolId, @PathVariable Long periodId) {
        TenantContext.requireMatches(schoolId);

        timetableFacade.deletePeriod(schoolId, periodId);
        return ResponseEntity.noContent().build();
    }

    // ---- فعالیت‌ها ----

    @GetMapping("/activities")
    public List<ActivityResponse> activities(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return timetableFacade.listActivities(schoolId).stream().map(ActivityResponse::from).toList();
    }

    @PostMapping("/activities")
    public ActivityResponse createActivity(@PathVariable Long schoolId, @RequestBody ActivityRequest request) {
        TenantContext.requireMatches(schoolId);

        return ActivityResponse.from(timetableFacade.createActivity(schoolId, request.name()));
    }

    @PutMapping("/activities/{activityId}")
    public ActivityResponse renameActivity(@PathVariable Long schoolId, @PathVariable Long activityId,
                                           @RequestBody ActivityRequest request) {
        TenantContext.requireMatches(schoolId);

        return ActivityResponse.from(timetableFacade.renameActivity(schoolId, activityId, request.name()));
    }

    @DeleteMapping("/activities/{activityId}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long schoolId, @PathVariable Long activityId) {
        TenantContext.requireMatches(schoolId);

        timetableFacade.deleteActivity(schoolId, activityId);
        return ResponseEntity.noContent().build();
    }

    // ---- انتخابگرها ----

    /** کلاس‌های مدرسه برای انتخابگرِ کلاس (بدون نیاز به CLASS_VIEW). */
    @GetMapping("/classes")
    public List<ClassResponse> classes(@PathVariable Long schoolId,
                                       @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // همان قاعده‌ی /classes: پیش‌فرض سالِ جاری، "all" فقط برای مدیر
        return yearVisibility.classListYear(schoolId, academicYear)
                .map(year -> academicFacade.listClasses(schoolId, year))
                .orElseGet(() -> academicFacade.listClasses(schoolId)).stream()
                .map(c -> new ClassResponse(c.id().value(), c.schoolId(), c.name(), c.teacherId(), c.section(),
                        c.gradeLevel(), c.gradingScheme(), c.academicYear()))
                .toList();
    }

    /** معلم‌های فعالِ مدرسه برای انتخابگرِ معلمِ هر خانه. */
    @GetMapping("/teachers")
    public List<TeacherResponse> teachers(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return iamFacade.findMembershipsBySchool(schoolId).stream()
                .filter(m -> "TEACHER".equals(m.roleCode()) && m.active())
                .map(m -> new TeacherResponse(m.userId().value(), m.fullName()))
                .distinct()
                .toList();
    }

    /** درس‌های هم‌پایه‌ی کلاس، شاملِ غیرنمره‌پذیر (برخلاف /grades/courses). */
    @GetMapping("/classes/{classId}/courses")
    public List<CourseResponse> courses(@PathVariable Long schoolId, @PathVariable Long classId,
                                        @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // سالِ اختیاری: فقط درس‌هایی که برای آن سال ارائه می‌شوند (بازه‌ی اعتبارِ درس)
        return timetableFacade.listCoursesForTimetable(schoolId, classId, academicYear).stream()
                .map(c -> new CourseResponse(c.id().value(), c.gradeLevel(), c.name()))
                .toList();
    }

    // ---- خانه‌های برنامه ----

    @GetMapping("/classes/{classId}")
    public List<EntryResponse> entries(@PathVariable Long schoolId, @PathVariable Long classId,
                                       @RequestParam String academicYear) {
        TenantContext.requireMatches(schoolId);

        return withTeacherNames(timetableFacade.listEntriesForClass(schoolId, classId, academicYear));
    }

    @PutMapping("/classes/{classId}/entries")
    public EntryResponse setEntry(@PathVariable Long schoolId, @PathVariable Long classId,
                                  @RequestBody EntryRequest request) {
        TenantContext.requireMatches(schoolId);

        TimetableFacade.EntryInfo entry = timetableFacade.setEntry(schoolId, classId, request.periodId(),
                parseDay(request.weekday()), request.courseId(), request.activityId(), request.teacherUserId());
        return withTeacherNames(List.of(entry)).get(0);
    }

    @DeleteMapping("/classes/{classId}/entries")
    public ResponseEntity<Void> clearEntry(@PathVariable Long schoolId, @PathVariable Long classId,
                                           @RequestParam Long periodId, @RequestParam String weekday) {
        TenantContext.requireMatches(schoolId);

        timetableFacade.clearEntry(schoolId, classId, periodId, parseDay(weekday));
        return ResponseEntity.noContent().build();
    }

    // فقط معلم‌هایی که در همین خانه‌ها آمده‌اند (هر شناسه یک بار)، نه همه‌ی عضوهای مدرسه
    private List<EntryResponse> withTeacherNames(List<TimetableFacade.EntryInfo> entries) {
        Map<Long, String> names = new HashMap<>();
        entries.stream().map(TimetableFacade.EntryInfo::teacherUserId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> names.put(id, iamFacade.findUser(new IamFacade.UserId(id))
                        .map(IamFacade.UserProfile::fullName).orElse(null)));
        return entries.stream()
                .map(e -> EntryResponse.from(e, e.teacherUserId() == null ? null : names.get(e.teacherUserId())))
                .toList();
    }

    static TimetableDay parseDay(String text) {
        try {
            return TimetableDay.valueOf(text == null ? "" : text.strip());
        } catch (IllegalArgumentException e) {
            throw new AcademicException(new AcademicError.InvalidTimetableEntry(
                    "روز هفته باید یکی از SAT، SUN، MON، TUE، WED، THU یا FRI باشد"));
        }
    }

    // دقیقاً HH:mm (مثل «08:00»)؛ LocalTime.parse ثانیه و کسر را هم می‌پذیرد و UI آن را پنهان می‌کرد
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    static LocalTime parseTime(String text) {
        try {
            return text == null ? null : LocalTime.parse(text.strip(), HH_MM);
        } catch (DateTimeParseException e) {
            throw new AcademicException(new AcademicError.InvalidPeriod("ساعت باید به شکل HH:mm باشد"));
        }
    }
}
