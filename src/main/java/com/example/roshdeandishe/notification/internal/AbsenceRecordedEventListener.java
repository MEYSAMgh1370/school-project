package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.attendance.AbsenceRecordedEvent;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * غیبتِ تازه‌ثبت‌شده (attendance.md «Notifications»): به همه‌ی ولی‌های لینک‌شده‌ی دانش‌آموز یک پیام می‌رود.
 * ولیِ بدونِ حسابِ بله‌ی لینک‌شده بی‌صدا رد می‌شود (قراردادِ {@code NotificationFacade.sendMessageToUser}:
 * {@code false}، بدونِ خطا). listener بعد از commitِ ذخیره‌ی حضور و غیاب در thread جدید اجرا می‌شود، پس
 * شکستِ ارسال هرگز ذخیره را خراب یا rollback نمی‌کند؛ هر خطایی همین‌جا گرفته و لاگ می‌شود تا یک ولیِ
 * خراب مانع بقیه نشود. {@code TenantContext} مثل بقیه‌ی listenerها در ابتدا ست و در finally پاک می‌شود.
 * <p>
 * تاریخ در پیام شمسی است (SchoolFacade.formatSolarDate، مثل «۱۴ مهر ۱۴۰۵»).
 */
@Component
class AbsenceRecordedEventListener {

    private static final Logger log = LoggerFactory.getLogger(AbsenceRecordedEventListener.class);

    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final NotificationFacade notificationFacade;

    AbsenceRecordedEventListener(AcademicFacade academicFacade, IamFacade iamFacade, SchoolFacade schoolFacade,
                                 NotificationFacade notificationFacade) {
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.notificationFacade = notificationFacade;
    }

    @ApplicationModuleListener
    void onAbsenceRecorded(AbsenceRecordedEvent event) {
        try {
            TenantContext.set(event.schoolId());

            Optional<StudentInfo> found = academicFacade.findStudentInfo(event.studentId());
            if (found.isEmpty()) {
                log.warn("اطلاع‌رسانی غیبت رد شد: دانش‌آموزی با شناسه {} وجود ندارد", event.studentId());
                return;
            }
            StudentInfo student = found.get();
            List<Long> parents = academicFacade.listGuardians(student.schoolId(), student.id().value());
            if (parents.isEmpty()) {
                return;
            }

            String studentName = iamFacade.findUser(new IamFacade.UserId(student.userId()))
                    .map(IamFacade.UserProfile::fullName).orElse(null);
            String schoolName = schoolFacade.findSchool(new SchoolId(event.schoolId()))
                    .map(SchoolFacade.SchoolInfo::name).orElse(null);
            String text = buildMessage(studentName, schoolName, schoolFacade.formatSolarDate(event.day()));

            for (Long parentUserId : parents) {
                try {
                    notificationFacade.sendMessageToUser(parentUserId, text);
                } catch (RuntimeException e) {
                    log.warn("ارسال اطلاع‌رسانی غیبت به ولیِ {} شکست خورد", parentUserId, e);
                }
            }
        } catch (RuntimeException e) {
            log.warn("اطلاع‌رسانی غیبتِ دانش‌آموز {} شکست خورد", event.studentId(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /** متنِ پیام: نامِ دانش‌آموز، نامِ مدرسه و تاریخِ شمسی (مثل «۱۴ مهر ۱۴۰۵»)؛ نامِ ناشناخته به «فرزند شما» برمی‌گردد. */
    static String buildMessage(String studentName, String schoolName, String solarDate) {
        String who = studentName == null || studentName.isBlank() ? "فرزند شما" : studentName.strip();
        StringBuilder text = new StringBuilder("غیبت: ").append(who);
        if (schoolName != null && !schoolName.isBlank()) {
            text.append(" در مدرسه‌ی ").append(schoolName.strip());
        }
        return text.append(" در تاریخ ").append(solarDate).append(" غایب ثبت شد.").toString();
    }
}
