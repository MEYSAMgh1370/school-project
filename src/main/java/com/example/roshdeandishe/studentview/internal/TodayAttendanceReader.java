package com.example.roshdeandishe.studentview.internal;

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
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.studentview.TodayAttendance;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * وضعیتِ امروزِ یک فرزند برای کارتِ «فرزندان من» (والد)، فقط از رابطِ عمومیِ {@link AttendanceFacade}.
 * <p>
 * {@code getClassDay} فهرستِ کلِ کلاس را برمی‌گرداند، پس اینجا فقط ردیفِ همین فرزند برداشته می‌شود و هیچ
 * داده‌ی دانش‌آموزِ دیگری از این کلاس بیرون نمی‌رود. کلاسی که هنوز حضور و غیابش گرفته نشده «ثبت نشده» است،
 * نه «حاضر» (همان قاعده‌ی رقمِ مدیر: کلاسِ ثبت‌نشده هرگز حاضر شمرده نمی‌شود). مدرسه‌ای که ماژول را ندارد یا
 * خطای دامنه‌ای پیش بیاید {@link TodayAttendance#UNAVAILABLE} می‌دهد؛ پیشخوان به‌خاطرِ یک نشانگرِ فرعی نباید بشکند.
 */
@Component
class TodayAttendanceReader {

    private static final String ATTENDANCE_MODULE = "ATTENDANCE";

    private final IamFacade iamFacade;
    private final AttendanceFacade attendanceFacade;
    private final SchoolFacade schoolFacade;

    TodayAttendanceReader(IamFacade iamFacade, AttendanceFacade attendanceFacade, SchoolFacade schoolFacade) {
        this.iamFacade = iamFacade;
        this.attendanceFacade = attendanceFacade;
        this.schoolFacade = schoolFacade;
    }

    public TodayAttendance todayFor(UserId userId, Long schoolId, Long classId, Long studentId) {
        if (classId == null) {
            return TodayAttendance.UNAVAILABLE;
        }
        boolean entitled = iamFacade.checkAccess(userId, new ModuleCode(ATTENDANCE_MODULE),
                new AccessContext.SchoolContext(schoolId)).isGranted();
        if (!entitled) {
            return TodayAttendance.UNAVAILABLE;
        }
        try {
            LocalDate today = today(schoolFacade.findSchool(new SchoolId(schoolId)));
            ClassDay day = attendanceFacade.getClassDay(schoolId, classId, today);
            if (!day.recordable()) {
                return TodayAttendance.NOT_SCHOOL_DAY;
            }
            if (!day.taken()) {
                return TodayAttendance.PENDING;
            }
            Optional<StudentMark> mark = day.students().stream()
                    .filter(m -> studentId.equals(m.studentId())).findFirst();
            if (mark.isEmpty()) {
                return TodayAttendance.UNAVAILABLE;
            }
            AttendanceStatus status = mark.get().status();
            if (status == null) {
                return TodayAttendance.PRESENT;
            }
            if (status == AttendanceStatus.LATE) {
                return TodayAttendance.LATE;
            }
            return mark.get().excused() ? TodayAttendance.ABSENT_EXCUSED : TodayAttendance.ABSENT;
        } catch (AttendanceException e) {
            return TodayAttendance.UNAVAILABLE;
        }
    }

    /** «امروز» به وقتِ مدرسه؛ منطقه‌ی ناشناخته یا نبودنِ مدرسه به منطقه‌ی سرور برمی‌گردد (مثل ماژول حضور و غیاب). */
    private static LocalDate today(Optional<SchoolInfo> school) {
        ZoneId zone;
        try {
            zone = school.map(SchoolInfo::timezone).map(ZoneId::of).orElseGet(ZoneId::systemDefault);
        } catch (DateTimeException e) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone);
    }
}
