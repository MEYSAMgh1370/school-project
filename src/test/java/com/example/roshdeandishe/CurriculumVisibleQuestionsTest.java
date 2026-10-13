package com.example.roshdeandishe;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.NewQuestion;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code CurriculumFacade.findQuestionsVisibleToSchool}: فقط سؤال‌های پلتفرمی و خودِ مدرسه، شناسه‌ی ناشناخته نمی‌آید.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CurriculumVisibleQuestionsTest {

    @Autowired
    private CurriculumFacade curriculumFacade;

    @Autowired
    private JdbcTemplate jdbc;

    private long schoolQuestion(long school) {
        long topic = curriculumFacade.createTopic(school, null, "سرفصل " + UUID.randomUUID(), 0).value();
        return curriculumFacade.createQuestion(school,
                new NewQuestion(topic, "سؤال", "الف", "ب", "ج", "د", 1, null, null, null)).value();
    }

    @Test
    void returnsOwnAndPlatformQuestionsButNotOtherSchoolsOrUnknown() {
        long schoolA = 910_001L;
        long schoolB = 910_002L;
        long own = schoolQuestion(schoolA);
        long foreign = schoolQuestion(schoolB);
        String code = "qbv-" + UUID.randomUUID().toString().substring(0, 8);
        curriculumFacade.upsertPlatformTopic(code + "-t", null, "سرفصل پلتفرمی", 0);
        curriculumFacade.upsertPlatformQuestion(code, code + "-t", "سؤال پلتفرمی", List.of("a", "b", "c", "d"), 2, null);
        long platform = jdbc.queryForObject("select id from curriculum.questions where source_code = ?", Long.class, code);

        List<QuestionInfo> visible = curriculumFacade.findQuestionsVisibleToSchool(
                schoolA, List.of(own, foreign, platform, 999_999_999L));

        assertThat(visible).extracting(q -> q.id().value()).containsExactlyInAnyOrder(own, platform);
        assertThat(curriculumFacade.findQuestionsVisibleToSchool(schoolA, List.of())).isEmpty();
    }
}
