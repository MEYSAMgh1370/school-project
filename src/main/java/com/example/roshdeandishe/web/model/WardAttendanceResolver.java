package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.ClassDay;
import com.example.roshdeandishe.attendance.AttendanceFacade.StudentMark;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * وضعیتِ امروزِ یک فرزند برای کارتِ «فرزندان من» (والد)، فقط از رابطِ عمومیِ {@link AttendanceFacade}.
 * <p>
 * {@code getClassDay} فهرستِ کلِ کلاس را برمی‌گرداند، پس اینجا فقط ردیفِ همین فرزند برداشته می‌شود و هیچ
 * داده‌ی دانش‌آموزِ دیگری از این کلاس بیرون نمی‌رود. کلاسی که هنوز حضور و غیابش گرفته نشده «ثبت نشده» است،
 * نه «حاضر» (همان قاعده‌ی رقمِ مدیر: کلاسِ ثبت‌نشده هرگز حاضر شمرده نمی‌شود). مدرسه‌ای که ماژول را ندارد یا
 * خطای دامنه‌ای پیش بیاید {@link WardAttendance#UNAVAILABLE} می‌دهد؛ پیشخوان به‌خاطرِ یک نشانگرِ فرعی نباید بشکند.
 */
@Component
public class WardAttendanceResolver {

    private static final String ATTENDANCE_MODULE = "ATTENDANCE";

    private final IamFacade iamFacade;
    private final AttendanceFacade attendanceFacade;
    private final SchoolFacade schoolFacade;

    WardAttendanceResolver(IamFacade iamFacade, AttendanceFacade attendanceFacade, SchoolFacade schoolFacade) {
        this.iamFacade = iamFacade;
        this.attendanceFacade = attendanceFacade;
        this.schoolFacade = schoolFacade;
    }

    public WardAttendance todayFor(UserId userId, Long schoolId, Long classId, Long studentId) {
        if (classId == null) {
            return WardAttendance.UNAVAILABLE;
        }
        boolean entitled = iamFacade.checkAccess(userId, new ModuleCode(ATTENDANCE_MODULE),
                new AccessContext.SchoolContext(schoolId)).isGranted();
        if (!entitled) {
            return WardAttendance.UNAVAILABLE;
        }
        try {
            LocalDate today = AttendanceTodayAssembler.today(schoolFacade.findSchool(new SchoolId(schoolId)));
            ClassDay day = attendanceFacade.getClassDay(schoolId, classId, today);
            if (!day.recordable()) {
                return WardAttendance.NOT_SCHOOL_DAY;
            }
            if (!day.taken()) {
                return WardAttendance.PENDING;
            }
            Optional<StudentMark> mark = day.students().stream()
                    .filter(m -> studentId.equals(m.studentId())).findFirst();
            if (mark.isEmpty()) {
                return WardAttendance.UNAVAILABLE;
            }
            AttendanceStatus status = mark.get().status();
            if (status == null) {
                return WardAttendance.PRESENT;
            }
            if (status == AttendanceStatus.LATE) {
                return WardAttendance.LATE;
            }
            return mark.get().excused() ? WardAttendance.ABSENT_EXCUSED : WardAttendance.ABSENT;
        } catch (AttendanceException e) {
            return WardAttendance.UNAVAILABLE;
        }
    }
}
