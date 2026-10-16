package com.example.roshdeandishe.studentview;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;

import java.util.List;

/**
 * رابطِ عمومیِ ماژول Student view: خلاصهٔ دانش‌آموز و وضعیتِ امروزِ حضور و غیاب، یکسان برای پیشخوان و ربات.
 */
public interface StudentViewFacade {

    /** {@code viewer}: کاربرِ واردشده (خودِ دانش‌آموز یا والدِ او)؛ فراخواننده رابطه را از قبل سنجیده است. */
    StudentSummary summary(UserId viewer, Long schoolId, Long studentId);

    /**
     * «خلاصهٔ من» برای هر مدرسه‌ای که کاربر در آن دانش‌آموزِ فعال و ثبت‌نام‌شده است (نقشِ فعالِ STUDENT +
     * ثبت‌نام)؛ یک ردیف به‌ازای هر مدرسه.
     */
    List<OwnSummary> ownSummaries(UserId userId, List<MembershipInfo> memberships);

    /** وضعیتِ امروزِ یک دانش‌آموز؛ {@link TodayAttendance#UNAVAILABLE} اگر ماژول یا کلاس در دسترس نباشد. */
    TodayAttendance todayAttendance(UserId viewer, Long schoolId, Long classId, Long studentId);

    /** خلاصهٔ خودِ دانش‌آموز در یک مدرسه. */
    record OwnSummary(Long schoolId, StudentSummary summary) {
    }
}
