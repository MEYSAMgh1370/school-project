package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;
import com.example.roshdeandishe.academic.dto.TimetableDtos.*;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * «برنامهٔ کلاسی من» - همان مدل اعتمادِ {@code MyGradesController}: بدون
 * {@code @RequiresPermission}/{@code @RequiresModule}، چون فقط داده‌ی خودِ کاربر
 * برمی‌گردد. اجتماعِ سه دید (یک کاربر می‌تواند هم‌زمان چند نقش داشته باشد):
 * کلاسِ خودِ دانش‌آموز، کلاسِ هر فرزندِ واقعاً لینک‌شده، و ساعت‌هایی که کاربر معلمِ
 * آن‌هاست. بیگانه نتیجه‌ی خالی می‌گیرد، نه خطا و نه دادهٔ دیگران.
 * <p>
 * فقط سالِ جاریِ مدرسه (تصمیمِ academic-year.md: غیرِ مدیر فقط سالِ جاری را
 * می‌بیند)؛ پارامترِ سال ندارد. نام معلم برای دانش‌آموز/ولی هم می‌آید.
 */
@RestController
public class MyTimetableController {

    private final TimetableFacade timetableFacade;
    private final AcademicFacade academicFacade;
    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public MyTimetableController(TimetableFacade timetableFacade, AcademicFacade academicFacade,
                                 SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.timetableFacade = timetableFacade;
        this.academicFacade = academicFacade;
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/timetable")
    public MyTimetableResponse myTimetable(@PathVariable Long schoolId) {
        Long userId = iamFacade.currentUserId().value();

        // هر دید به یک عضویتِ *فعال* با نقشِ متناظر در همین مدرسه بسته است: ثبت‌نام، لینکِ ولی و
        // خانه‌ی معلم با غیرفعال‌شدنِ عضویت پاک نمی‌شوند، پس بدونِ این بررسی عضوِ سابق همچنان
        // برنامه‌ی مدرسه را می‌گرفت. هر نقش عضویتِ جدا دارد، پس نقش‌ها جدا بررسی می‌شوند.
        java.util.Set<String> activeRoles = new java.util.HashSet<>();
        iamFacade.findMembershipsByUser(new IamFacade.UserId(userId)).stream()
                .filter(m -> schoolId.equals(m.schoolId()) && m.active())
                .forEach(m -> activeRoles.add(m.roleCode()));

        // اول هویت: بدونِ هیچ دیدی، حتی سالِ جاریِ مدرسه هم برای بیگانه برنگردد
        List<Pending> pending = new ArrayList<>();
        if (activeRoles.contains("STUDENT")) {
            // دانش‌آموزِ بدونِ کلاس (ارتقایافته و هنوز جا‌نشده، فارغ‌التحصیل) برنامه‌ای ندارد
            academicFacade.findMyEnrollment(userId, schoolId).filter(e -> e.classId() != null)
                    .ifPresent(e -> pending.add(new Pending("OWN", e.studentId().value(), null, e.classId().value())));
        }
        if (activeRoles.contains("PARENT")) {
            for (AcademicFacade.StudentInfo ward : academicFacade.listWards(userId, schoolId)) {
                if (ward.classId() == null) {
                    continue;
                }
                pending.add(new Pending("WARD", ward.id().value(), ward.userId(), ward.classId().value()));
            }
        }

        String year = schoolFacade.findCurrentAcademicYear(new SchoolFacade.SchoolId(schoolId))
                .map(AcademicYearInfo::code).orElse(null);
        // معلمِ فعال همیشه دیدِ «تدریس» دارد، حتی بدونِ خانه یا بدونِ سالِ جاری، تا صفحه حالتِ خالیِ
        // درست را نشان دهد؛ پاسخِ «بیگانه» فقط برای کسی است که هیچ دیدی ندارد.
        boolean activeTeacher = activeRoles.contains("TEACHER");
        List<EntryInfo> teaching = year == null || !activeTeacher ? List.of()
                : timetableFacade.listEntriesForTeacher(schoolId, year, userId);
        if (pending.isEmpty() && !activeTeacher) {
            return new MyTimetableResponse(null, List.of(), List.of());
        }

        // مدرسه هنوز سالِ جاری را انتخاب نکرده: دیدها بدون خانه برمی‌گردند تا صفحه همین را بگوید
        List<Long> classIds = pending.stream().map(Pending::classId).distinct().toList();
        List<EntryInfo> classEntries = year == null ? List.of()
                : timetableFacade.listEntriesForClasses(schoolId, year, classIds);

        Map<Long, String> classNames = new HashMap<>();
        if (!pending.isEmpty()) {
            academicFacade.listClasses(schoolId).forEach(c -> classNames.put(c.id().value(), c.name()));
        }
        Map<Long, String> names = new HashMap<>();
        List<MyTimetableView> views = new ArrayList<>();
        for (Pending p : pending) {
            List<EntryInfo> entries = classEntries.stream().filter(e -> e.classId().equals(p.classId())).toList();
            views.add(new MyTimetableView(p.kind(), p.studentId(),
                    p.wardUserId() == null ? null : name(names, p.wardUserId()), p.classId(),
                    classNames.get(p.classId()),
                    entries.stream().map(e -> toResponse(e, names)).toList()));
        }
        if (activeTeacher) {
            views.add(new MyTimetableView("TEACHING", null, null, null, null,
                    teaching.stream().map(e -> toResponse(e, names)).toList()));
        }
        List<PeriodResponse> periods = year == null ? List.<PeriodResponse>of()
                : timetableFacade.listPeriods(schoolId, year).stream().map(PeriodResponse::from).toList();
        return new MyTimetableResponse(year, periods, views);
    }

    private EntryResponse toResponse(EntryInfo e, Map<Long, String> names) {
        return EntryResponse.from(e, e.teacherUserId() == null ? null : name(names, e.teacherUserId()));
    }

    private String name(Map<Long, String> cache, Long userId) {
        return cache.computeIfAbsent(userId,
                id -> iamFacade.findUser(new IamFacade.UserId(id)).map(IamFacade.UserProfile::fullName).orElse(null));
    }

    private record Pending(String kind, Long studentId, Long wardUserId, Long classId) {
    }
}
