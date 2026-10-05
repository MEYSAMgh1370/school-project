package com.example.roshdeandishe.attendance.controller;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.dto.request.SubmitExcuseRequest;
import com.example.roshdeandishe.attendance.dto.response.ExcuseResponse;
import com.example.roshdeandishe.attendance.dto.response.MyAttendanceResponse;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * حضور و غیابِ «من/فرزندانم» و ثبتِ عذرِ ولی (openspec/changes/attendance.md) - همان مدل اعتمادِ
 * {@code finance.MyInvoicesController}: بدونِ {@code @RequiresPermission}، چون داده فقط درباره‌ی خودِ کاربرِ
 * احراز هویت‌شده است و هر بخش به نقشِ فعالِ خودش بسته است (STUDENT برای خودش، PARENT برای فرزندانِ لینک‌شده؛
 * ولیِ دانش‌آموزِ بیگانه هیچ‌چیز نمی‌گیرد، facade این را اعمال می‌کند).
 * <p>
 * مسیر /api/me است، پس TenantResolverFilter آن را معاف می‌کند و {@code TenantContext} خالی است؛
 * {@code @RequiresModule} در این حالت PlatformContext می‌سازد و درست نیست، پس اشتراکِ ماژول
 * {@code ATTENDANCE} همین‌جا و با {@code IamFacade.checkAccess} روی مدرسه‌ی مسیر سنجیده می‌شود (همان خطای
 * {@code module-not-entitled}).
 */
@RestController
public class MyAttendanceController {

    private static final String MODULE = "ATTENDANCE";

    private final AttendanceFacade attendanceFacade;
    private final IamFacade iamFacade;

    public MyAttendanceController(AttendanceFacade attendanceFacade, IamFacade iamFacade) {
        this.attendanceFacade = attendanceFacade;
        this.iamFacade = iamFacade;
    }

    /** غیبت و دیرآمدنِ خودِ دانش‌آموز و فرزندانِ لینک‌شده در بازه‌ی [from, to] (حداکثر ۳۶۶ روز). */
    @GetMapping("/api/me/schools/{schoolId}/attendance")
    public MyAttendanceResponse myAttendance(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = requireEntitledUser(schoolId);

        return MyAttendanceResponse.from(attendanceFacade.listMyAttendance(schoolId, userId, from, to));
    }

    /** ثبتِ عذرِ ولی برای غیبتِ فرزند؛ دوباره‌فرستادن تا وقتی PENDING است یادداشت را عوض می‌کند. */
    @PostMapping("/api/me/schools/{schoolId}/attendance/excuses")
    public ExcuseResponse submitExcuse(@PathVariable Long schoolId, @RequestBody SubmitExcuseRequest request) {
        Long userId = requireEntitledUser(schoolId);

        return ExcuseResponse.from(attendanceFacade.submitExcuse(
                schoolId, request.studentId(), request.day(), userId, request.note()));
    }

    private Long requireEntitledUser(Long schoolId) {
        IamFacade.UserId userId = iamFacade.currentUserId();
        AccessDecision decision = iamFacade.checkAccess(userId, new IamFacade.ModuleCode(MODULE),
                new AccessContext.SchoolContext(schoolId));
        if (decision instanceof AccessDecision.Denied denied) {
            throw new IamException(new IamError.ModuleNotEntitled(MODULE, schoolId, denied.reason()));
        }
        return userId.value();
    }
}
