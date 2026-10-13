package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.iam.IamFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * باکس سؤالات رایگان است (فرض ثبت‌شده در openspec/changes/question-box.md، منتظر تأیید): همان الگوی
 * {@link AttendanceEntitlementBackfill}؛ مدرسه‌های موجود اینجا و مدرسه‌های تازه هنگام ساخت
 * ({@code grantFreeTierEntitlements}) ماژول را می‌گیرند. ردیفِ موجود، حتی غیرفعال، دست نمی‌خورد.
 * {@code @Order(20)}: بعد از {@code IamSeedDataRunner}.
 */
@Component
@Order(20)
class QuestionBoxEntitlementBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionBoxEntitlementBackfill.class);
    private static final IamFacade.ModuleCode QUESTION_BOX = new IamFacade.ModuleCode("QUESTION_BOX");

    private final SchoolRepository schoolRepository;
    private final IamFacade iamFacade;

    QuestionBoxEntitlementBackfill(SchoolRepository schoolRepository, IamFacade iamFacade) {
        this.schoolRepository = schoolRepository;
        this.iamFacade = iamFacade;
    }

    @Override
    public void run(ApplicationArguments args) {
        int granted = backfill();
        if (granted > 0) {
            log.info("ماژول باکس سؤالات برای {} مدرسه‌ی موجود فعال شد", granted);
        }
    }

    /** تعدادِ مدرسه‌هایی که همین الان ردیفِ entitlement گرفتند (اجرای دوم: صفر). */
    int backfill() {
        int granted = 0;
        for (SchoolEntity school : schoolRepository.findAll()) {
            if (iamFacade.ensureSchoolEntitlement(school.getId(), QUESTION_BOX)) {
                granted++;
            }
        }
        return granted;
    }
}
