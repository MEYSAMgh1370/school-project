package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * بخشِ «حضور و غیاب امروز» پیشخوانِ مدیر، فقط از رابطِ عمومیِ {@link AttendanceFacade} (بدونِ وابستگی به
 * internalهای ماژول حضور و غیاب). همان الگوی «وضعیت شهریه»: سمت سرور و به‌ازای هر مدرسه‌ی مدیر.
 * <p>
 * {@code AttendanceFacade} خودش entitlement را نمی‌سنجد (کنترلرِ REST با {@code @RequiresModule} می‌سنجد)،
 * پس اینجا اول {@code checkAccess} روی ماژولِ {@code ATTENDANCE} صدا زده می‌شود: مدرسه‌ای که ماژول را فعال
 * ندارد (یا مدیر عمداً خاموشش کرده) هیچ بخشی نمی‌گیرد و خطایی هم رخ نمی‌دهد. «امروز» به وقتِ منطقه‌ی
 * زمانیِ مدرسه است (همان قاعده‌ی خودِ ماژول).
 */
@Component
public class AttendanceTodayAssembler {

    private static final String ATTENDANCE_MODULE = "ATTENDANCE";

    private final IamFacade iamFacade;
    private final AttendanceFacade attendanceFacade;

    AttendanceTodayAssembler(IamFacade iamFacade, AttendanceFacade attendanceFacade) {
        this.iamFacade = iamFacade;
        this.attendanceFacade = attendanceFacade;
    }

    /**
     * @return خالی اگر مدرسه ماژول حضور و غیاب را ندارد، یا خواندنِ خلاصه با خطای دامنه شکست خورد
     *         (پیشخوان به‌خاطرِ یک بخشِ فرعی نباید بشکند)
     */
    public Optional<AttendanceTodayRow> forAdminSchool(UserId userId, Long schoolId, String schoolName,
                                                       Optional<SchoolInfo> school) {
        boolean entitled = iamFacade.checkAccess(userId, new ModuleCode(ATTENDANCE_MODULE),
                new AccessContext.SchoolContext(schoolId)).isGranted();
        if (!entitled) {
            return Optional.empty();
        }
        try {
            DaySummary summary = attendanceFacade.summaryForDay(schoolId, today(school));
            return Optional.of(new AttendanceTodayRow(schoolId, schoolName, summary.schoolDay(), summary.absent(),
                    summary.presentPercent(), summary.classesTaken(), summary.classesTotal()));
        } catch (AttendanceException e) {
            return Optional.empty();
        }
    }

    /** «امروز» به وقتِ مدرسه؛ منطقه‌ی ناشناخته یا نبودنِ مدرسه به منطقه‌ی سرور برمی‌گردد (مثل ماژول حضور و غیاب). */
    static LocalDate today(Optional<SchoolInfo> school) {
        ZoneId zone;
        try {
            zone = school.map(SchoolInfo::timezone).map(ZoneId::of).orElseGet(ZoneId::systemDefault);
        } catch (DateTimeException e) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone);
    }
}
