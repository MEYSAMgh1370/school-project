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
 * تصویرِ سؤال‌های باکس (openspec/changes/question-box.md): ذخیره به‌صورت bytea، بررسی نوع با بایت‌های ابتدایی (نه
 * نوعِ اعلامی)، حجم، سقفِ تعداد، دسترسی و جداییِ مدرسه‌ها.
 */
class QuestionBoxImagesFlowIntegrationTest extends QuestionBoxTestSupport {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static final String IMAGE_ONLY_MCQ =
            "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"IMAGE_ONLY\",\"correctChoice\":3}";

    private long createQuestion(Fixture f, MockHttpSession who, String body) throws Exception {
        return json(send(who, f.school(), post(f.base() + "/questions"), body).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private ResultActions upload(Fixture f, MockHttpSession who, long questionId, String role, String name,
                                 String type, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart(f.base() + "/questions/" + questionId + "/images")
                .file(new MockMultipartFile("file", name, type, bytes))
                .param("role", role)
                .with(csrf()).header("X-School-ID", String.valueOf(f.school())).session(who));
    }

    private long uploadOk(Fixture f, MockHttpSession who, long questionId, String role) throws Exception {
        return json(upload(f, who, questionId, role, "a.png", "image/png", PNG).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    @Test
    void uploadPngAndFetchBytesBack() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("شکل را توضیح دهید"));
        upload(f, f.teacher1(), q, "STEM", "a.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STEM"));
        long imageId = json(send(f.teacher2(), f.school(), get(f.base() + "/questions/" + q), null))
                .get("images").get(0).get("id").asLong();
        byte[] body = send(f.teacher2(), f.school(), get(f.base() + "/images/" + imageId), null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void rejectsTextFileRenamedToPng() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        upload(f, f.teacher1(), q, "STEM", "a.png", "image/png", "<html>hi</html>".getBytes())
                .andExpect(status().isBadRequest());
        upload(f, f.teacher1(), q, "STEM", "a.png", "image/png", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversize() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        upload(f, f.teacher1(), q, "STEM", "big.png", "image/png", big).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSeventhImage() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        for (int i = 0; i < 6; i++) {
            uploadOk(f, f.teacher1(), q, "STEM");
        }
        upload(f, f.teacher1(), q, "STEM", "a.png", "image/png", PNG).andExpect(status().isBadRequest());
    }

    @Test
    void choiceImageOnEssayIsRejected() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        upload(f, f.teacher1(), q, "CHOICE_1", "a.png", "image/png", PNG).andExpect(status().isBadRequest());
    }

    @Test
    void onlyEditorMayUploadOrRemove() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        long image = uploadOk(f, f.teacher1(), q, "STEM");
        upload(f, f.teacher2(), q, "STEM", "a.png", "image/png", PNG).andExpect(status().isForbidden());
        send(f.teacher2(), f.school(), delete(f.base() + "/questions/" + q + "/images/" + image), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void otherSchoolImageIs404() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long q = createQuestion(a, a.teacher1(), essay("س"));
        long image = uploadOk(a, a.teacher1(), q, "STEM");
        send(b.teacher1(), b.school(), get(b.base() + "/images/" + image), null).andExpect(status().isNotFound());
    }

    @Test
    void removeImage() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), essay("س"));
        long image = uploadOk(f, f.teacher1(), q, "ANSWER");
        send(f.teacher1(), f.school(), delete(f.base() + "/questions/" + q + "/images/" + image), null)
                .andExpect(status().isNoContent());
        send(f.teacher1(), f.school(), get(f.base() + "/images/" + image), null).andExpect(status().isNotFound());
        JsonNode question = json(send(f.teacher1(), f.school(), get(f.base() + "/questions/" + q), null));
        assertThat(question.get("images")).isEmpty();
    }

    @Test
    void imageOnlyQuestionWithoutStemIsNotComplete() throws Exception {
        Fixture f = fixture();
        long q = createQuestion(f, f.teacher1(), IMAGE_ONLY_MCQ);
        send(f.teacher1(), f.school(), get(f.base() + "/questions/" + q), null)
                .andExpect(jsonPath("$.complete").value(false));
        uploadOk(f, f.teacher1(), q, "STEM");
        send(f.teacher1(), f.school(), get(f.base() + "/questions/" + q), null)
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.images.length()").value(1));
    }

    @Test
    void imageOnlyQuestionRejectsChoiceTexts() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/questions"),
                "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"IMAGE_ONLY\",\"correctChoice\":1,\"choices\":[\"a\",\"b\",\"c\",\"d\"]}")
                .andExpect(status().isBadRequest());
    }
}
