package com.example.roshdeandishe.attendance.controller;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.MarkInput;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.dto.request.SaveRollRequest;
import com.example.roshdeandishe.attendance.dto.response.ClassAbsenteesResponse;
import com.example.roshdeandishe.attendance.dto.response.ClassDayResponse;
import com.example.roshdeandishe.attendance.dto.response.DaySummaryResponse;
import com.example.roshdeandishe.attendance.dto.response.RecordableClassResponse;
import com.example.roshdeandishe.attendance.dto.response.ReportRowResponse;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * حضور و غیاب، سمتِ معلم و مدیر (openspec/changes/attendance.md). ماژول {@code ATTENDANCE} روی کلاس گیت
 * می‌شود. روزها ISO (yyyy-MM-dd) هستند.
 * <p>
 * <b>ثبت و خواندنِ یک کلاس</b> «یکی از دو مجوز» می‌خواهد: {@code ATTENDANCE_RECORD} (معلم، فقط کلاسِ خودش) یا
 * {@code ATTENDANCE_RECORD_ANY} (مدیر، هر کلاس). {@code @RequiresPermission} فقط یک مجوز را می‌سنجد و
 * «یا» را نمی‌تواند بیان کند، پس این سه endpoint انوتیشنِ مجوز ندارند و همین‌جا از راه
 * {@code IamFacade.checkPermission} (همان سازوکارِ {@code AcademicYearVisibility}) سنجیده می‌شوند، پیش از هر
 * خواندن/نوشتن. بقیه (دیدِ سراسری و گزارش) تک‌مجوزی‌اند: {@code @RequiresPermission("ATTENDANCE_VIEW_ALL")}.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/attendance")
@RequiresModule("ATTENDANCE")
public class AttendanceController {

    private static final String RECORD = "ATTENDANCE_RECORD";
    private static final String RECORD_ANY = "ATTENDANCE_RECORD_ANY";
    private static final String EDIT_PAST = "ATTENDANCE_EDIT_PAST";

    private final AttendanceFacade attendanceFacade;
    private final IamFacade iamFacade;

    public AttendanceController(AttendanceFacade attendanceFacade, IamFacade iamFacade) {
        this.attendanceFacade = attendanceFacade;
        this.iamFacade = iamFacade;
    }

    /** کلاس‌هایی که کاربر می‌تواند برایشان ثبت کند (انتخابگرِ صفحه): معلم کلاس‌های خودش، مدیر همه‌ی کلاس‌های سالِ جاری. */
    @GetMapping("/classes")
    public List<RecordableClassResponse> classes(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        TenantContext.requireMatches(schoolId);
        Recorder recorder = requireRecorder(schoolId);

        return attendanceFacade.listRecordableClasses(schoolId, recorder.userId(), recorder.any(), day).stream()
                .map(RecordableClassResponse::from)
                .toList();
    }

    @GetMapping("/classes/{classId}/days/{day}")
    public ClassDayResponse classDay(
            @PathVariable Long schoolId, @PathVariable Long classId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        TenantContext.requireMatches(schoolId);
        Recorder recorder = requireRecorder(schoolId);
        attendanceFacade.requireMayAccessClass(schoolId, classId, recorder.userId(), recorder.any());

        return ClassDayResponse.from(attendanceFacade.getClassDay(schoolId, classId, day));
    }

    @PutMapping("/classes/{classId}/days/{day}")
    public ClassDayResponse saveRoll(
            @PathVariable Long schoolId, @PathVariable Long classId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestBody SaveRollRequest request) {
        TenantContext.requireMatches(schoolId);
        Recorder recorder = requireRecorder(schoolId);
        attendanceFacade.requireMayAccessClass(schoolId, classId, recorder.userId(), recorder.any());
        attendanceFacade.requireMayEditDay(schoolId, day, hasPermission(schoolId, EDIT_PAST).isGranted());

        List<MarkInput> marks = request.marks() == null ? null : request.marks().stream()
                .map(m -> new MarkInput(m.studentId(), parseStatus(m.status()), Boolean.TRUE.equals(m.excused()),
                        m.note()))
                .toList();
        return ClassDayResponse.from(attendanceFacade.saveRoll(schoolId, classId, day, recorder.userId(), marks));
    }

    @GetMapping("/days/{day}/absentees")
    @RequiresPermission("ATTENDANCE_VIEW_ALL")
    public List<ClassAbsenteesResponse> absentees(
            @PathVariable Long schoolId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        TenantContext.requireMatches(schoolId);

        return attendanceFacade.absenteesForDay(schoolId, day).stream().map(ClassAbsenteesResponse::from).toList();
    }

    @GetMapping("/days/{day}/summary")
    @RequiresPermission("ATTENDANCE_VIEW_ALL")
    public DaySummaryResponse summary(
            @PathVariable Long schoolId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        TenantContext.requireMatches(schoolId);

        return DaySummaryResponse.from(attendanceFacade.summaryForDay(schoolId, day));
    }

    /** گزارشِ بازه: برای هر دانش‌آموز روزهای غایب، دیرآمده و حاضر؛ {@code classId} و {@code name} اختیاری‌اند. */
    @GetMapping("/report")
    @RequiresPermission("ATTENDANCE_VIEW_ALL")
    public List<ReportRowResponse> report(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String name) {
        TenantContext.requireMatches(schoolId);

        return attendanceFacade.report(schoolId, from, to, classId, name).stream().map(ReportRowResponse::from).toList();
    }

    // ---- «ثبت‌کننده»: یکی از دو مجوز ----

    private record Recorder(Long userId, boolean any) {
    }

    /** ATTENDANCE_RECORD_ANY (هر کلاس) یا ATTENDANCE_RECORD (کلاسِ خود)؛ وگرنه 403 permission-denied. */
    private Recorder requireRecorder(Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        if (hasPermission(schoolId, RECORD_ANY).isGranted()) {
            return new Recorder(userId, true);
        }
        AccessDecision own = hasPermission(schoolId, RECORD);
        if (own instanceof AccessDecision.Denied denied) {
            throw new IamException(new IamError.PermissionDenied(RECORD, schoolId, denied.reason()));
        }
        return new Recorder(userId, false);
    }

    private AccessDecision hasPermission(Long schoolId, String code) {
        return iamFacade.checkPermission(iamFacade.currentUserId(), new IamFacade.PermissionCode(code),
                new AccessContext.SchoolContext(schoolId));
    }

    private static AttendanceStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return AttendanceStatus.valueOf(status.strip());
        } catch (IllegalArgumentException e) {
            throw new AttendanceException(new AttendanceError.InvalidMark("وضعیت باید ABSENT یا LATE باشد."));
        }
    }
}
