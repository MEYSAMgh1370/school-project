package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.web.dto.WardRef;
import com.example.roshdeandishe.web.dto.WardRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * فرزندانِ لینک‌شده‌ی یک والد برای بخش «فرزندان من» داشبورد: برای هر مدرسه‌ای که
 * کاربر در آن والدِ فعال است، فرزندان (academic)، نام (iam)، نام مدرسه (school)،
 * کلاس (academic) و شمارش فاکتورها (finance) کنار هم گذاشته می‌شود.
 * <p>
 * همان الگوی scope کردنِ MyInvoicesController و دستور «فرزندان من» ربات بله:
 * فقط فرزندانی که واقعاً به همین کاربر لینک شده‌اند ({@code listWards}) و فقط
 * داده‌ی خودِ آن‌ها - بدون TenantContext، چون صفحه‌ی خودسرویس است.
 */
@Component
public class WardsAssembler {

    private static final String PARENT_ROLE = "PARENT";
    private static final String OWN_GRADES_LABEL = "نمره‌های من";

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final AcademicFacade academicFacade;
    private final FinanceFacade financeFacade;

    WardsAssembler(IamFacade iamFacade, SchoolFacade schoolFacade,
                   AcademicFacade academicFacade, FinanceFacade financeFacade) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.academicFacade = academicFacade;
        this.financeFacade = financeFacade;
    }

    /** آیا کاربر در حداقل یک مدرسه والدِ فعال است؟ (تفاوتِ «والد بدون فرزند لینک‌شده» با «والد نیست») */
    public static boolean isParent(List<MembershipInfo> memberships) {
        return memberships.stream().anyMatch(m -> m.active() && PARENT_ROLE.equals(m.roleCode()));
    }

    /**
     * فقط نامِ فرزندانِ لینک‌شده‌ی کاربرِ فعلی در همه‌ی مدرسه‌هایی که عضویتِ
     * فعال (با هر نقشی) دارد - برای صفحه‌ی «نمره‌های من». دامنه عمداً با
     * {@code MyGradesController} یکی است: آن endpoint فرزندان را مستقل از نقش
     * (listWards) برمی‌گرداند، پس مثلاً معلمِ فعالی که عضویتِ والدش غیرفعال
     * شده هم نامِ فرزندش را می‌بیند و با دانش‌آموزِ خودش اشتباه نمی‌شود. بدون
     * فراخوانی finance. خودِ کاربر (اگر دانش‌آموزِ آن مدرسه است) هم با
     * {@code own = true} می‌آید، حتی بدون نمره.
     */
    public List<WardRef> wardRefsForCurrentUser() {
        UserId userId = iamFacade.currentUserId();
        List<WardRef> refs = new ArrayList<>();
        for (Long schoolId : activeSchoolIds(iamFacade.findMembershipsByUser(userId))) {
            // خودِ کاربر اگر دانش‌آموزِ این مدرسه است - حتی بدون هیچ نمره‌ای -
            // تا «نمره‌های من» از روی ردیف‌های نمره حدس زده نشود.
            Optional<EnrollmentInfo> own = academicFacade.findMyEnrollment(userId.value(), schoolId);
            own.ifPresent(enrollment -> refs.add(
                    new WardRef(enrollment.studentId().value(), schoolId, OWN_GRADES_LABEL, true)));
            for (StudentInfo ward : academicFacade.listWards(userId.value(), schoolId)) {
                if (own.isPresent() && own.get().studentId().equals(ward.id())) {
                    continue;
                }
                refs.add(new WardRef(ward.id().value(), schoolId, wardName(ward), false));
            }
        }
        return List.copyOf(refs);
    }

    private static Set<Long> activeSchoolIds(List<MembershipInfo> memberships) {
        Set<Long> schoolIds = new LinkedHashSet<>();
        for (MembershipInfo membership : memberships) {
            if (membership.active()) {
                schoolIds.add(membership.schoolId());
            }
        }
        return schoolIds;
    }

    private static Set<Long> parentSchoolIds(List<MembershipInfo> memberships) {
        Set<Long> schoolIds = new LinkedHashSet<>();
        for (MembershipInfo membership : memberships) {
            if (membership.active() && PARENT_ROLE.equals(membership.roleCode())) {
                schoolIds.add(membership.schoolId());
            }
        }
        return schoolIds;
    }

    private String wardName(StudentInfo ward) {
        return iamFacade.findUser(new UserId(ward.userId()))
                .map(UserProfile::fullName)
                .orElse("دانش‌آموز #" + ward.userId());
    }

    public List<WardRow> forParent(UserId userId, List<MembershipInfo> memberships) {
        Set<Long> schoolIds = parentSchoolIds(memberships);

        List<WardRow> rows = new ArrayList<>();
        for (Long schoolId : schoolIds) {
            List<StudentInfo> wards = academicFacade.listWards(userId.value(), schoolId);
            if (wards.isEmpty()) {
                continue;
            }
            String schoolName = schoolFacade.findSchool(new SchoolId(schoolId))
                    .map(school -> school.name())
                    .orElse("مدرسه #" + schoolId);
            List<InvoiceInfo> invoices = financeFacade.listInvoicesForStudents(schoolId,
                    wards.stream().map(ward -> ward.id().value()).toList());

            for (StudentInfo ward : wards) {
                String name = wardName(ward);
                Optional<EnrollmentInfo> enrollment = academicFacade.findMyEnrollment(ward.userId(), schoolId);

                List<InvoiceInfo> mine = invoices.stream()
                        .filter(invoice -> invoice.studentId().equals(ward.id().value()))
                        .toList();
                int overdue = (int) mine.stream().filter(i -> i.status() == InvoiceStatus.OVERDUE).count();
                int open = (int) mine.stream()
                        .filter(i -> i.status() == InvoiceStatus.UNPAID || i.status() == InvoiceStatus.PARTIAL)
                        .count();

                rows.add(new WardRow(ward.id().value(), schoolId, name, schoolName,
                        enrollment.map(EnrollmentInfo::className).orElse(null),
                        enrollment.map(EnrollmentInfo::section).orElse(null),
                        mine.size(), overdue, open));
            }
        }
        return List.copyOf(rows);
    }
}
