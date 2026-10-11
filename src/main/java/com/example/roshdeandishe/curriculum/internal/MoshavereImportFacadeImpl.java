package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.MoshavereImportFacade;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
class MoshavereImportFacadeImpl implements MoshavereImportFacade {

    private static final int FORMAT = 1;
    private static final Set<String> TRACKS = Set.of("ریاضی", "تجربی", "انسانی");

    private final CurriculumFacade curriculumFacade;
    private final AcademicFacade academicFacade;
    private final TopicRepository topicRepository;

    MoshavereImportFacadeImpl(CurriculumFacade curriculumFacade, AcademicFacade academicFacade,
                              TopicRepository topicRepository) {
        this.curriculumFacade = curriculumFacade;
        this.academicFacade = academicFacade;
        this.topicRepository = topicRepository;
    }

    @Override
    @Transactional
    public ImportSummary importBundle(Bundle bundle, boolean dryRun) {
        // اعتبارسنجی پیش از هر نوشتن
        validate(bundle);

        int topicsCreated = 0;
        int topicsUpdated = 0;
        for (TopicEntry t : bundle.topics()) {
            String code = t.sourceCode().strip();
            boolean existed = topicRepository.findBySourceCodeAndSchoolIdIsNull(code).isPresent();
            curriculumFacade.upsertPlatformTopic(code, t.parentSourceCode(), t.name(),
                    t.sortOrder() == null ? 0 : t.sortOrder());
            if (existed) {
                topicsUpdated++;
            } else {
                topicsCreated++;
            }
        }

        int coursesCreated = 0;
        int coursesUpdated = 0;
        int coursesUnlinked = 0;
        for (CourseEntry c : bundle.courses()) {
            Long topicId = null;
            if (c.topicSourceCode() != null) {
                topicId = topicRepository.findBySourceCodeAndSchoolIdIsNull(c.topicSourceCode().strip())
                        .map(TopicEntity::getId)
                        .orElse(null);
            }
            if (topicId == null) {
                coursesUnlinked++;
            }
            boolean existed = academicFacade.platformCourseExists(c.gradeLevel(), c.name());
            academicFacade.upsertPlatformCourse(c.gradeLevel(), c.name(), c.track(), topicId);
            if (existed) {
                coursesUpdated++;
            } else {
                coursesCreated++;
            }
        }

        if (dryRun) {
            // تراکنشِ بیرونی rollback می‌شود ولی پاسخ عادی ساخته می‌شود (استثنا نمی‌اندازیم)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new ImportSummary(topicsCreated, topicsUpdated, coursesCreated, coursesUpdated, coursesUnlinked, dryRun);
    }

    private static void validate(Bundle bundle) {
        if (bundle == null || bundle.format() == null || bundle.format() != FORMAT) {
            throw invalid("قالب بسته باید ۱ باشد.");
        }
        List<TopicEntry> topics = bundle.topics();
        List<CourseEntry> courses = bundle.courses();
        if (topics == null || courses == null) {
            throw invalid("فهرست‌های topics و courses الزامی‌اند.");
        }

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < topics.size(); i++) {
            TopicEntry t = topics.get(i);
            String at = "سرفصل شماره‌ی " + (i + 1) + ": ";
            if (t == null || t.sourceCode() == null || t.sourceCode().isBlank() || t.sourceCode().strip().length() > 32) {
                throw invalid(at + "کد منبع باید بین ۱ تا ۳۲ نویسه باشد.");
            }
            String code = t.sourceCode().strip();
            if (t.name() == null || t.name().isBlank()) {
                throw invalid(at + "نام سرفصل «" + code + "» خالی است.");
            }
            if (t.name().strip().length() > 250) {
                throw invalid(at + "نام سرفصل «" + code + "» بیش از ۲۵۰ نویسه است.");
            }
            if (!seen.add(code)) {
                throw invalid(at + "کد منبع «" + code + "» تکراری است.");
            }
            if (t.parentSourceCode() != null && !t.parentSourceCode().isBlank()
                    && !seen.contains(t.parentSourceCode().strip())) {
                // والد باید زودتر در فهرست آمده باشد؛ خودِ سرفصل هم در seen است ولی والدِ خودش بودن را رد می‌کنیم
                throw invalid(at + "والد «" + t.parentSourceCode().strip() + "» برای «" + code
                        + "» پیش از آن در فهرست نیامده است.");
            }
            if (t.parentSourceCode() != null && t.parentSourceCode().strip().equals(code)) {
                throw invalid(at + "سرفصل «" + code + "» والدِ خودش است.");
            }
        }

        for (int i = 0; i < courses.size(); i++) {
            CourseEntry c = courses.get(i);
            String at = "درس شماره‌ی " + (i + 1) + ": ";
            if (c == null || c.gradeLevel() == null || c.gradeLevel() < 1 || c.gradeLevel() > 12) {
                throw invalid(at + "پایه باید بین ۱ تا ۱۲ باشد.");
            }
            if (c.name() == null || c.name().isBlank()) {
                throw invalid(at + "نام درس خالی است.");
            }
            if (c.name().strip().length() > 128) {
                throw invalid(at + "نام درس «" + c.name().strip() + "» بیش از ۱۲۸ نویسه است.");
            }
            if (c.track() != null && !TRACKS.contains(c.track())) {
                throw invalid(at + "رشته‌ی «" + c.track() + "» برای درس «" + c.name().strip() + "» نامعتبر است.");
            }
            if (c.topicSourceCode() != null && !seen.contains(c.topicSourceCode().strip())) {
                throw invalid(at + "کد سرفصل «" + c.topicSourceCode().strip() + "» برای درس «" + c.name().strip()
                        + "» در بسته نیست.");
            }
        }
    }

    private static CurriculumException invalid(String reason) {
        return new CurriculumException(new CurriculumError.InvalidMoshavereBundle(reason));
    }
}
