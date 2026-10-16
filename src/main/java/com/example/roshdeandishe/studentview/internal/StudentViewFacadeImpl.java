package com.example.roshdeandishe.studentview.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.studentview.StudentSummary;
import com.example.roshdeandishe.studentview.StudentViewFacade;
import com.example.roshdeandishe.studentview.TodayAttendance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** پیاده‌سازیِ {@link StudentViewFacade}؛ به readerهای داخلی واگذار می‌کند. */
@Component
class StudentViewFacadeImpl implements StudentViewFacade {

    private static final String STUDENT_ROLE = "STUDENT";

    private final AcademicFacade academicFacade;
    private final StudentSummaryReader summaryReader;
    private final TodayAttendanceReader attendanceReader;

    StudentViewFacadeImpl(AcademicFacade academicFacade, StudentSummaryReader summaryReader,
                          TodayAttendanceReader attendanceReader) {
        this.academicFacade = academicFacade;
        this.summaryReader = summaryReader;
        this.attendanceReader = attendanceReader;
    }

    @Override
    public StudentSummary summary(UserId viewer, Long schoolId, Long studentId) {
        return summaryReader.forStudent(viewer, schoolId, studentId);
    }

    @Override
    public List<OwnSummary> ownSummaries(UserId userId, List<MembershipInfo> memberships) {
        List<OwnSummary> rows = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (MembershipInfo m : memberships) {
            if (!m.active() || !STUDENT_ROLE.equals(m.roleCode()) || !seen.add(m.schoolId())) {
                continue;
            }
            academicFacade.findMyEnrollment(userId.value(), m.schoolId()).ifPresent(enrollment ->
                    rows.add(new OwnSummary(m.schoolId(),
                            summaryReader.forStudent(userId, m.schoolId(), enrollment.studentId().value()))));
        }
        return rows;
    }

    @Override
    public TodayAttendance todayAttendance(UserId viewer, Long schoolId, Long classId, Long studentId) {
        return attendanceReader.todayFor(viewer, schoolId, classId, studentId);
    }
}
