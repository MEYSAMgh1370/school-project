package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed داده‌ی dev: یک مدرسه‌ی نمونه می‌سازد تا بلافاصله بعد از بالا آمدن
 * اپلیکیشن، یک schoolId واقعی برای تست دستی (Postman/curl با هدر
 * X-School-ID) در دسترس باشد. عمداً در ماژول school است، نه iam - چون
 * این ماژول مالک SchoolEntity است؛ iam هیچ وابستگی‌ای به این Seeder ندارد.
 */
@Component
class SchoolSeedDataRunner implements ApplicationRunner {

    private final SchoolRepository schoolRepository;

    SchoolSeedDataRunner(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (schoolRepository.count() > 0) {
            return;
        }
        SchoolEntity demoSchool = new SchoolEntity("مدرسه‌ی نمونه", "Asia/Tehran", "fa-IR");
        demoSchool.setStatus(SchoolStatus.ACTIVE);
        schoolRepository.save(demoSchool);
    }
}
