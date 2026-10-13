package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.StudentStatus;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.RecordableClass;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.web.model.TeacherClassRow.AttendanceState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * «کلاس‌های من» پیشخوانِ معلم، فقط از رابط‌های عمومیِ {@link TimetableFacade}، {@link AcademicFacade} و
 * {@link AttendanceFacade}. همان الگوی {@link AttendanceTodayAssembler}: سمت سرور و به‌ازای هر مدرسه‌ی معلم.
 * <p>
 * با ماژولِ حضور و غیاب (entitlement) کلاس‌ها از {@code listRecordableClasses} می‌آیند - همان قاعده‌ی «کلاسِ
 * خود» که صفحه‌ی حضور و غیاب اعمال می‌کند، در سالِ جاری و با وضعیتِ «ثبت شده/نشده» امروز. بدونِ آن ماژول، فهرست
 * از {@code listClassesTaughtBy} (همه‌ی سال‌ها) و بدونِ وضعیت می‌آید.
 */
@Component
public class TeacherClassesAssembler {

    private static final String ATTENDANCE_MODULE = "ATTENDANCE";

    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;
    private final TimetableFacade timetableFacade;
    private final AttendanceFacade attendanceFacade;

    TeacherClassesAssembler(IamFacade iamFacade, AcademicFacade academicFacade, TimetableFacade timetableFacade,
                            AttendanceFacade attendanceFacade) {
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
        this.timetableFacade = timetableFacade;
        this.attendanceFacade = attendanceFacade;
    }

    public TeacherClassesRow forTeacherSchool(UserId userId, Long schoolId, String schoolName) {
        boolean entitled = iamFacade.checkAccess(userId, new ModuleCode(ATTENDANCE_MODULE),
                new AccessContext.SchoolContext(schoolId)).isGranted();
        if (entitled) {
            try {
                List<TeacherClassRow> rows = attendanceFacade
                        .listRecordableClasses(schoolId, userId.value(), false, null).stream()
                        .map(c -> row(schoolId, c))
                        .toList();
                return new TeacherClassesRow(schoolId, schoolName, rows);
            } catch (AttendanceException e) {
                // پیشخوان به‌خاطرِ یک بخشِ فرعی نباید بشکند؛ به فهرستِ بدونِ وضعیت برمی‌گردیم.
            }
        }
        List<TeacherClassRow> rows = timetableFacade.listClassesTaughtBy(schoolId, userId.value()).stream()
                .map(c -> row(schoolId, c))
                .toList();
        return new TeacherClassesRow(schoolId, schoolName, rows);
    }

    private TeacherClassRow row(Long schoolId, RecordableClass c) {
        return new TeacherClassRow(c.classId(), c.name(), c.section(), c.gradeLevel(),
                activeStudents(schoolId, c.classId()), c.taken() ? AttendanceState.TAKEN : AttendanceState.PENDING);
    }

    private TeacherClassRow row(Long schoolId, ClassInfo c) {
        return new TeacherClassRow(c.id().value(), c.name(), c.section(), c.gradeLevel(),
                activeStudents(schoolId, c.id().value()), AttendanceState.UNAVAILABLE);
    }

    /** فقط دانش‌آموزانِ فعالِ همین کلاس (نه کسانی که در سالی در آن بوده‌اند و حالا جای دیگرند). */
    private int activeStudents(Long schoolId, Long classId) {
        return (int) academicFacade.listStudentsInClass(schoolId, classId).stream()
                .filter(s -> s.status() == StudentStatus.ACTIVE
                        && s.classId() != null && classId.equals(s.classId().value()))
                .count();
    }
}
