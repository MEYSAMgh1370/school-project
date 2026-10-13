package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.util.Base64;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * بایت‌های تصویر فقط وقتی خوانده می‌شوند که خودِ تصویر خواسته شده باشد: فهرست سؤال‌ها، سربرگ‌ها و نمای نهایی برگه
 * نباید ستونِ bytea را از دیتابیس بکشند (هر درخواست صدها مگابایت به heap می‌آورد). شکلِ SQL با یک
 * StatementInspector سنجیده می‌شود.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.example.roshdeandishe.SqlCapture")
class QuestionBoxImageBytesFlowIntegrationTest extends QuestionBoxTestSupport {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static final Pattern HEADER_IMAGE_COLUMN = Pattern.compile("\\.image\\s*(,|from\\b)");

    private static boolean readsQuestionImageBytes(String sql) {
        return sql.contains("question_images") && sql.contains(".data");
    }

    private static boolean readsHeaderImageBytes(String sql) {
        return sql.contains("questionbox.headers") && HEADER_IMAGE_COLUMN.matcher(sql).find();
    }

    private long questionWithImage(Fixture f, MockHttpSession who) throws Exception {
        long id = json(send(who, f.school(), post(f.base() + "/questions"), essay("سؤال با تصویر"))
                .andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(multipart(f.base() + "/questions/" + id + "/images")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .param("role", "STEM")
                .with(csrf()).header("X-School-ID", String.valueOf(f.school())).session(who)).andExpect(status().isCreated());
        return id;
    }

    @Test
    void listingAndReadingQuestionsNeverSelectsImageBytes() throws Exception {
        Fixture f = fixture();
        long id = questionWithImage(f, f.teacher1());

        SqlCapture.STATEMENTS.clear();
        send(f.teacher2(), f.school(), get(f.base() + "/questions"), null).andExpect(status().isOk());
        send(f.teacher2(), f.school(), get(f.base() + "/questions/" + id), null).andExpect(status().isOk());

        assertThat(SqlCapture.STATEMENTS).as("SQL that reads question_images.data").noneMatch(QuestionBoxImageBytesFlowIntegrationTest::readsQuestionImageBytes);
    }

    @Test
    void resolvingASheetNeverSelectsImageBytes() throws Exception {
        Fixture f = fixture();
        long id = questionWithImage(f, f.teacher1());
        long sheet = json(send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                "{\"title\":\"برگه\",\"sections\":[{\"title\":\"بخش\",\"items\":[{\"source\":\"BOX\",\"questionId\":" + id + "}]}]}")
                .andExpect(status().isCreated())).get("id").asLong();

        SqlCapture.STATEMENTS.clear();
        send(f.teacher1(), f.school(), get(f.base() + "/sheets/" + sheet + "/resolved"), null).andExpect(status().isOk());

        assertThat(SqlCapture.STATEMENTS).noneMatch(QuestionBoxImageBytesFlowIntegrationTest::readsQuestionImageBytes);
    }

    @Test
    void listingHeadersAndResolvingAHeaderedSheetNeverSelectsTheHeaderImage() throws Exception {
        Fixture f = fixture();
        long header = json(send(f.teacher1(), f.school(), post(f.base() + "/headers"),
                "{\"name\":\"تصویری\",\"mode\":\"IMAGE\"}").andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(multipart(f.base() + "/headers/" + header + "/image")
                .file(new MockMultipartFile("file", "h.png", "image/png", PNG))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .with(csrf()).header("X-School-ID", String.valueOf(f.school())).session(f.teacher1())).andExpect(status().isOk());
        long sheet = json(send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                "{\"title\":\"برگه\",\"headerId\":" + header + ",\"sections\":[{\"title\":\"بخش\",\"items\":[]}]}")
                .andExpect(status().isCreated())).get("id").asLong();

        SqlCapture.STATEMENTS.clear();
        send(f.teacher2(), f.school(), get(f.base() + "/headers"), null).andExpect(status().isOk());
        send(f.teacher2(), f.school(), get(f.base() + "/sheets/" + sheet + "/resolved"), null).andExpect(status().isOk());

        assertThat(SqlCapture.STATEMENTS).as("SQL that reads headers.image").noneMatch(QuestionBoxImageBytesFlowIntegrationTest::readsHeaderImageBytes);
    }
}
