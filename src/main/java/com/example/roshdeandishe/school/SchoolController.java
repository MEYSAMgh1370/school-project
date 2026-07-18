package com.example.roshdeandishe.school;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.web.bind.annotation.*;

/**
 * ساخت مدرسه عمداً خودسرویسی (self-service) است، نه یک عملیات ادمین
 * پلتفرمی: هر کاربر احراز هویت‌شده می‌تواند یک مدرسه بسازد و بلافاصله
 * SCHOOL_ADMIN همان مدرسه می‌شود. این تصمیم آگاهانه است تا نیازی به
 * زیرساخت PLATFORM_ADMIN (که هنوز ساخته نشده - PermissionDecisionService
 * فعلاً فقط از SchoolContext پشتیبانی می‌کند) در این فاز نباشد.
 * <p>
 * ⚠️ عملیات‌های واقعاً پلتفرمی مثل toggle کردن Entitlement یک مدرسه یا
 * تعلیق یک مدرسه (setSchoolStatus) عمداً اینجا expose نشده‌اند - آن‌ها
 * نیازمند یک مدل دسترسی پلتفرمی واقعی هستند که هنوز طراحی نشده.
 */
@RestController
@RequestMapping("/api/admin/schools")
public class SchoolController {

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public SchoolController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @PostMapping
    public SchoolResponse createSchool(@RequestBody CreateSchoolRequest request) {
        SchoolId schoolId = schoolFacade.createSchool(request.name(), request.timezone(), request.locale(), request.level());

        // سازنده‌ی مدرسه بلافاصله SCHOOL_ADMIN همان مدرسه می‌شود
        IamFacade.UserId callerId = iamFacade.currentUserId();
        iamFacade.createMembership(callerId, schoolId.value(), "SCHOOL_ADMIN");

        SchoolInfo info = schoolFacade.findSchool(schoolId)
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        return SchoolResponse.from(info);
    }

    @GetMapping("/{schoolId}")
    public SchoolResponse getSchool(@PathVariable Long schoolId) {
        // به‌جای یک permission check پلتفرمی (که هنوز وجود ندارد)، حداقل
        // شرط معقول: کاربر باید عضوی از این مدرسه باشد تا بتواند اطلاعاتش
        // را ببیند - از عمد یک راه‌حل موقت ساده‌تر از ساختن زیرساخت کامل
        // PLATFORM_ADMIN است.
        IamFacade.UserId callerId = iamFacade.currentUserId();
        MembershipInfo membership = iamFacade.findMembership(callerId, schoolId)
                .orElseThrow(() -> new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId)));

        if (!membership.active()) {
            throw new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId));
        }

        SchoolInfo info = schoolFacade.findSchool(new SchoolId(schoolId))
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId)));
        return SchoolResponse.from(info);
    }

    public record CreateSchoolRequest(String name, String timezone, String locale, SchoolFacade.SchoolLevel level) {
    }

    public record SchoolResponse(Long id, String name, String status, String timezone, String locale, String level) {
        static SchoolResponse from(SchoolInfo info) {
            return new SchoolResponse(
                    info.id().value(), info.name(), info.status().name(), info.timezone(), info.locale(),
                    info.level().name()
            );
        }
    }
}
