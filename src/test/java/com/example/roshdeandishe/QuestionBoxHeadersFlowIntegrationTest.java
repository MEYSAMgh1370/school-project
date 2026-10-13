package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * سربرگ‌های برگه‌ی چاپی (openspec/changes/question-box.md): قالب آماده (LAYOUT) یا تصویر (IMAGE)، قاعده‌ی ویرایش
 * مثل سؤال‌ها و جداییِ مدرسه‌ها.
 */
class QuestionBoxHeadersFlowIntegrationTest extends QuestionBoxTestSupport {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static String layoutHeader(String name) {
        return "{\"name\":\"" + name + "\",\"mode\":\"LAYOUT\",\"layout\":\"CLASSIC\",\"showSchoolName\":true,"
                + "\"showLogo\":false,\"fields\":[\"TITLE\",\"CLASS\",\"DATE\"]}";
    }

    private static final String IMAGE_HEADER = "{\"name\":\"سربرگ تصویری\",\"mode\":\"IMAGE\"}";

    private long create(Fixture f, MockHttpSession who, String body) throws Exception {
        return json(send(who, f.school(), post(f.base() + "/headers"), body).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private ResultActions uploadImage(Fixture f, MockHttpSession who, long headerId, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart(f.base() + "/headers/" + headerId + "/image")
                .file(new MockMultipartFile("file", "h.png", "image/png", bytes))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .with(csrf()).header("X-School-ID", String.valueOf(f.school())).session(who));
    }

    @Test
    void createLayoutHeaderWithFields() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/headers"), layoutHeader("کلاسیک"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("LAYOUT"))
                .andExpect(jsonPath("$.layout").value("CLASSIC"))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void layoutModeNeedsALayout() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/headers"),
                "{\"name\":\"بی‌قالب\",\"mode\":\"LAYOUT\",\"fields\":[\"TITLE\"]}")
                .andExpect(status().isBadRequest());
        send(f.teacher1(), f.school(), post(f.base() + "/headers"),
                "{\"name\":\"  \",\"mode\":\"LAYOUT\",\"layout\":\"CLASSIC\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void fieldsPersistAsSet() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/headers"),
                "{\"name\":\"تکراری\",\"mode\":\"LAYOUT\",\"layout\":\"COMPACT\",\"fields\":[\"TITLE\",\"TITLE\",\"DATE\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.length()").value(2));
    }

    @Test
    void imageModeRequiresImageBeforeUse() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), IMAGE_HEADER);
        send(f.teacher1(), f.school(), get(f.base() + "/headers"), null)
                .andExpect(jsonPath("$[0].complete").value(false))
                .andExpect(jsonPath("$[0].hasImage").value(false));
        uploadImage(f, f.teacher1(), id, PNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.hasImage").value(true));
        byte[] body = send(f.teacher2(), f.school(), get(f.base() + "/headers/" + id + "/image"), null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void imageModeRejectsNonImage() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), IMAGE_HEADER);
        uploadImage(f, f.teacher1(), id, "<html/>".getBytes()).andExpect(status().isBadRequest());
    }

    @Test
    void layoutModeRejectsImage() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), layoutHeader("قالب"));
        uploadImage(f, f.teacher1(), id, PNG).andExpect(status().isBadRequest());
    }

    @Test
    void editRulesMatchQuestions() throws Exception {
        Fixture f = fixture();
        long id = create(f, f.teacher1(), layoutHeader("مال معلم یک"));
        send(f.teacher2(), f.school(), put(f.base() + "/headers/" + id), layoutHeader("تغییر"))
                .andExpect(status().isForbidden());
        send(f.teacher2(), f.school(), delete(f.base() + "/headers/" + id), null).andExpect(status().isForbidden());
        send(f.admin(), f.school(), put(f.base() + "/headers/" + id), layoutHeader("ویرایش مدیر"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ویرایش مدیر"));
        iamFacade.setMembershipActive(f.school(), f.teacher1MembershipId(), false);
        send(f.teacher2(), f.school(), put(f.base() + "/headers/" + id), layoutHeader("تصاحب"))
                .andExpect(status().isOk());
        send(f.teacher2(), f.school(), delete(f.base() + "/headers/" + id), null).andExpect(status().isNoContent());
    }

    @Test
    void otherSchoolHeaderIs404() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long id = create(a, a.teacher1(), IMAGE_HEADER);
        uploadImage(a, a.teacher1(), id, PNG).andExpect(status().isOk());
        send(b.admin(), b.school(), get(b.base() + "/headers/" + id + "/image"), null).andExpect(status().isNotFound());
        send(b.admin(), b.school(), put(b.base() + "/headers/" + id), layoutHeader("x")).andExpect(status().isNotFound());
        JsonNode list = json(send(b.teacher1(), b.school(), get(b.base() + "/headers"), null));
        assertThat(list).isEmpty();
    }
}
