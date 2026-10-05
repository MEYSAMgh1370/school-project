package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.iam.IamFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * حضور و غیاب رایگان است (openspec/changes/attendance.md، تصمیم ۶): مدرسه‌های تازه آن را هنگام ساخت می‌گیرند
 * ({@code grantFreeTierEntitlements}) و مدرسه‌های موجود اینجا. به‌ازای هر مدرسه idempotent است (نه شرطِ
 * «شمارِ ردیف‌ها»، که مدرسه‌ی بعدی را هرگز نمی‌دید): ردیفی که هست، حتی غیرفعال، دست نمی‌خورد، پس مدیری که
 * ماژول را عمداً خاموش کرده با ریستارت دوباره روشن نمی‌شود. در production هم اجرا می‌شود (دادهٔ مرجع است، نه
 * نمایشی). مدرسه در school زندگی می‌کند و iam مدرسه‌ای نمی‌شناسد، پس حلقه اینجاست و ردیف را iam می‌سازد.
 * <p>
 * {@code @Order(20)}: بعد از {@code IamSeedDataRunner} ({@code @Order(10)}) که ردیفِ ماژولِ ATTENDANCE را می‌سازد.
 */
@Component
@Order(20)
class AttendanceEntitlementBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEntitlementBackfill.class);
    private static final IamFacade.ModuleCode ATTENDANCE = new IamFacade.ModuleCode("ATTENDANCE");

    private final SchoolRepository schoolRepository;
    private final IamFacade iamFacade;

    AttendanceEntitlementBackfill(SchoolRepository schoolRepository, IamFacade iamFacade) {
        this.schoolRepository = schoolRepository;
        this.iamFacade = iamFacade;
    }

    @Override
    public void run(ApplicationArguments args) {
        int granted = backfill();
        if (granted > 0) {
            log.info("ماژول حضور و غیاب برای {} مدرسه‌ی موجود فعال شد", granted);
        }
    }

    /** تعدادِ مدرسه‌هایی که همین الان ردیفِ entitlement گرفتند (اجرای دوم: صفر). */
    int backfill() {
        int granted = 0;
        for (SchoolEntity school : schoolRepository.findAll()) {
            if (iamFacade.ensureSchoolEntitlement(school.getId(), ATTENDANCE)) {
                granted++;
            }
        }
        return granted;
    }
}
