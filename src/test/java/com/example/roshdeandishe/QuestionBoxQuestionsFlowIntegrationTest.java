package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * باکس سؤالات (openspec/changes/question-box.md): سؤال‌های معلم، قاعده‌ی ویرایش (سازنده، مدیر، سازنده‌ی جداشده)،
 * جداییِ مدرسه‌ها و حذفِ نرم، روی Postgres واقعی.
 */
class QuestionBoxQuestionsFlowIntegrationTest extends QuestionBoxTestSupport {

    private long create(Fixture f, org.springframework.mock.web.MockHttpSession who, String body) throws Exception {
        return json(send(who, f.school(), post(f.base() + "/questions"), body).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    @Test
    void teacherCreatesMultipleChoiceAndEssay() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/questions"), mcq("۲+۲ چند است؟"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.choices.length()").value(4))
                .andExpect(jsonPath("$.correctChoice").value(2))
                .andExpect(jsonPath("$.editable").value(true));
        send(f.teacher1(), f.school(), post(f.base() + "/questions"), essay("اتم را توضیح دهید."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ESSAY"))
                .andExpect(jsonPath("$.choices.length()").value(0))
                .andExpect(jsonPath("$.correctChoice").doesNotExist());
    }

    @Test
    void essayWithChoicesIsRejected() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/questions"),
                "{\"type\":\"ESSAY\",\"contentMode\":\"TEXT\",\"text\":\"س\",\"choices\":[\"a\",\"b\",\"c\",\"d\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void multipleChoiceNeedsFourChoicesAndCorrectChoiceOneToFour() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/questions"),
                "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"TEXT\",\"text\":\"س\",\"choices\":[\"a\",\"b\"],\"correctChoice\":1}")
                .andExpect(status().isBadRequest());
        send(f.teacher1(), f.school(), post(f.base() + "/questions"),
                "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"TEXT\",\"text\":\"س\",\"choices\":[\"a\",\"b\",\"c\",\"d\"],\"correctChoice\":5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void textModeNeedsText() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/questions"),
                "{\"type\":\"ESSAY\",\"contentMode\":\"TEXT\",\"text\":\"  \"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherTeacherSeesButCannotEdit() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), essay("سؤال معلم یک"));
        JsonNode list = json(send(f.teacher2(), f.school(), get(f.base() + "/questions"), null)
                .andExpect(status().isOk()));
        assertThat(list).anySatisfy(q -> {
            assertThat(q.get("id").asLong()).isEqualTo(id);
            assertThat(q.get("editable").asBoolean()).isFalse();
        });
        send(f.teacher2(), f.school(), put(f.base() + "/questions/" + id), essay("تغییر"))
                .andExpect(status().isForbidden());
        send(f.teacher2(), f.school(), delete(f.base() + "/questions/" + id), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorEditsOwnQuestion() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), essay("قدیمی"));
        send(f.teacher1(), f.school(), put(f.base() + "/questions/" + id), essay("جدید"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("جدید"));
    }

    @Test
    void adminCanEditAny() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), essay("سؤال معلم"));
        send(f.admin(), f.school(), put(f.base() + "/questions/" + id), essay("ویرایش مدیر"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ویرایش مدیر"));
        send(f.admin(), f.school(), delete(f.base() + "/questions/" + id), null).andExpect(status().isNoContent());
    }

    @Test
    void orphanedQuestionEditableByAnyTeacher() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), essay("سؤال معلم جداشده"));
        iamFacade.setMembershipActive(f.school(), f.teacher1MembershipId(), false);
        send(f.teacher2(), f.school(), put(f.base() + "/questions/" + id), essay("تصاحب"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("تصاحب"));
    }

    @Test
    void otherSchoolQuestionIs404() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long id = create(a, a.teacher1(), essay("مال مدرسه‌ی الف"));
        send(b.teacher1(), b.school(), get(b.base() + "/questions/" + id), null).andExpect(status().isNotFound());
        send(b.admin(), b.school(), put(b.base() + "/questions/" + id), essay("x")).andExpect(status().isNotFound());
        JsonNode list = json(send(b.teacher1(), b.school(), get(b.base() + "/questions"), null)
                .andExpect(status().isOk()));
        assertThat(list).isEmpty();
    }

    @Test
    void deleteIsSoft() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), essay("برای حذف"));
        send(f.teacher1(), f.school(), delete(f.base() + "/questions/" + id), null).andExpect(status().isNoContent());
        assertThat(json(send(f.teacher1(), f.school(), get(f.base() + "/questions"), null))).isEmpty();
        send(f.teacher1(), f.school(), get(f.base() + "/questions/" + id), null).andExpect(status().isNotFound());
        Integer softDeleted = jdbc.queryForObject(
                "select count(*) from questionbox.questions where id = ? and deleted_at is not null", Integer.class, id);
        assertThat(softDeleted).isEqualTo(1);
    }

    @Test
    void listFiltersByTypeAndSearch() throws Exception {
        Fixture f = fixture();
        create(f, f.teacher1(), mcq("سؤال هندسه"));
        create(f, f.teacher1(), essay("تشریحی ریاضی"));
        assertThat(json(send(f.teacher1(), f.school(), get(f.base() + "/questions?type=ESSAY"), null))).hasSize(1);
        assertThat(json(send(f.teacher1(), f.school(), get(f.base() + "/questions?search=هندسه"), null))).hasSize(1);
        assertThat(json(send(f.teacher1(), f.school(), get(f.base() + "/questions"), null))).hasSize(2);
    }

    @Test
    void studentGets403() throws Exception {
        Fixture f = fixture();
        send(f.student(), f.school(), get(f.base() + "/questions"), null).andExpect(status().isForbidden());
        send(f.student(), f.school(), post(f.base() + "/questions"), essay("x")).andExpect(status().isForbidden());
    }
}
