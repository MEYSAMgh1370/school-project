package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.school.SchoolFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * گزارشِ نمره‌هایی که سالِ تحصیلی‌شان در فهرستِ سال‌ها نیست (openspec/changes/academic-year.md،
 * فاز ۱): سالِ نمره پیش‌تر متنِ آزاد بود. هیچ ردیفی تغییر نمی‌کند یا حذف نمی‌شود و هیچ سالی حدس زده
 * نمی‌شود؛ فقط یک WARNING با شمارِ هر (مدرسه، سال) تا مدیرِ پلتفرم بداند چه چیزی با فهرست نمی‌خواند.
 * مدیرِ مدرسه همچنان این ردیف‌ها را با نوشتنِ همان سال در دفتر نمرات می‌بیند.
 */
@Component
class GradeYearAudit implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GradeYearAudit.class);

    private final GradeRepository gradeRepository;
    private final SchoolFacade schoolFacade;

    GradeYearAudit(GradeRepository gradeRepository, SchoolFacade schoolFacade) {
        this.gradeRepository = gradeRepository;
        this.schoolFacade = schoolFacade;
    }

    @Override
    public void run(ApplicationArguments args) {
        report();
    }

    /** برای تست: تعدادِ (مدرسه، سال)های خارج از فهرست که گزارش شد. */
    int report() {
        Set<String> listed = new HashSet<>();
        schoolFacade.listAcademicYears().forEach(year -> listed.add(year.code()));
        int unmatched = 0;
        for (Object[] row : gradeRepository.countBySchoolAndYear()) {
            String year = (String) row[1];
            if (!listed.contains(year)) {
                unmatched++;
                log.warn("نمره‌هایی با سالِ تحصیلیِ خارج از فهرست: مدرسه {}، سال «{}»، {} ردیف (تغییری داده نشد)",
                        row[0], year, row[2]);
            }
        }
        return unmatched;
    }
}
