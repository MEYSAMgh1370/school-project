package com.example.roshdeandishe.studentview.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.GradeInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.MyDayAttendance;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.ExamFacade.ExamInfo;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.studentview.StudentSummary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * «خلاصهٔ من» / خلاصهٔ هر فرزند روی پیشخوان، فقط از رابط‌های عمومیِ facadeها (changes/student-summary.md).
 * <p>
 * هر سه بخش «بهترین تلاش»اند: خطای یک بخش پیشخوان را نمی‌شکند. فقط نمره‌های سالِ جاری شمرده می‌شود (همان
 * قاعده‌ی {@code MyGradesController} برای غیرمدیر). غیبت فقط از روزهای ثبت‌شده شمرده می‌شود: روزی که
 * حضور و غیابش گرفته نشده نه غایب است و نه حاضر. میانگین عمداً حساب نمی‌شود (تعریفش تصمیمِ محصول است).
 */
@Component
class StudentSummaryReader {

    static final int MAX_LINES = 3;
    private static final String ATTENDANCE_MODULE = "ATTENDANCE";
    private static final Map<String, String> LEVELS = Map.of(
            "EXCELLENT", "خیلی خوب", "GOOD", "خوب", "ACCEPTABLE", "قابل قبول", "NEEDS_EFFORT", "نیازمند تلاش");

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final AcademicFacade academicFacade;
    private final ExamFacade examFacade;
    private final AttendanceFacade attendanceFacade;

    StudentSummaryReader(IamFacade iamFacade, SchoolFacade schoolFacade, AcademicFacade academicFacade,
                            ExamFacade examFacade, AttendanceFacade attendanceFacade) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.academicFacade = academicFacade;
        this.examFacade = examFacade;
        this.attendanceFacade = attendanceFacade;
    }

    /** {@code viewer}: کاربرِ واردشده (خودِ دانش‌آموز یا والدِ او)؛ فراخواننده رابطه را از قبل سنجیده است. */
    public StudentSummary forStudent(UserId viewer, Long schoolId, Long studentId) {
        Optional<AcademicYearInfo> year = schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId));
        return new StudentSummary(latestGrades(schoolId, studentId, year), upcomingExams(schoolId, studentId),
                absences(viewer, schoolId, studentId, year));
    }

    private List<StudentSummary.GradeLine> latestGrades(Long schoolId, Long studentId, Optional<AcademicYearInfo> year) {
        try {
            return academicFacade.listGradesForStudents(schoolId, List.of(studentId)).stream()
                    .filter(g -> year.isEmpty() || year.get().code().equals(g.academicYear()))
                    .sorted(Comparator.comparing(GradeInfo::recordedAt).reversed())
                    .limit(MAX_LINES)
                    .map(StudentSummaryReader::gradeLine)
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static StudentSummary.GradeLine gradeLine(GradeInfo g) {
        String value;
        if (g.scheme() == GradingScheme.DESCRIPTIVE) {
            value = g.descriptiveLevel() == null ? "—" : LEVELS.getOrDefault(g.descriptiveLevel().name(), "—");
        } else {
            value = g.numericScore() == null ? "—" : g.numericScore().stripTrailingZeros().toPlainString();
        }
        return new StudentSummary.GradeLine(g.courseName() == null ? "نمره‌ی کل" : g.courseName(),
                g.term() == null ? "" : g.term().name().equals("FIRST") ? "نوبت اول" : "نوبت دوم", value);
    }

    private List<StudentSummary.ExamLine> upcomingExams(Long schoolId, Long studentId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            return examFacade.listExamsForStudent(schoolId, studentId).stream()
                    .filter(e -> e.end() != null && e.end().isAfter(now))
                    .sorted(Comparator.comparing(ExamInfo::start))
                    .limit(MAX_LINES)
                    .map(e -> new StudentSummary.ExamLine(e.title(), schoolFacade.formatSolarDate(e.start().toLocalDate())))
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private StudentSummary.AttendanceCount absences(UserId viewer, Long schoolId, Long studentId,
                                                    Optional<AcademicYearInfo> year) {
        if (year.isEmpty()
                || !iamFacade.checkAccess(viewer, new ModuleCode(ATTENDANCE_MODULE),
                        new AccessContext.SchoolContext(schoolId)).isGranted()) {
            return null;
        }
        try {
            LocalDate from = year.get().startDate();
            LocalDate to = year.get().endDate();
            LocalDate today = LocalDate.now();
            if (to == null || to.isAfter(today)) {
                to = today;
            }
            if (from == null || from.isAfter(to)) {
                return new StudentSummary.AttendanceCount(0, 0, 0);
            }
            List<MyDayAttendance> days = attendanceFacade.listMyAttendance(schoolId, viewer.value(), from, to).stream()
                    .filter(s -> studentId.equals(s.studentId()))
                    .flatMap(s -> s.days().stream())
                    .toList();
            int late = (int) days.stream().filter(d -> d.status() == AttendanceStatus.LATE).count();
            int excused = (int) days.stream()
                    .filter(d -> d.status() == AttendanceStatus.ABSENT && d.excused()).count();
            int absent = (int) days.stream()
                    .filter(d -> d.status() == AttendanceStatus.ABSENT && !d.excused()).count();
            return new StudentSummary.AttendanceCount(absent, excused, late);
        } catch (AttendanceException | IllegalArgumentException e) {
            return null;
        }
    }
}
