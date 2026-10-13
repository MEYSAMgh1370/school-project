package com.example.roshdeandishe;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.NewQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * برگه‌های ذخیره‌شده (openspec/changes/question-box.md): بخش‌ها، آیتم‌ها از باکس یا بانک، نمای نهایی، حذف نرم،
 * سربرگ، قاعده‌ی ویرایش و جداییِ مدرسه‌ها، روی Postgres واقعی.
 */
class QuestionBoxSheetsFlowIntegrationTest extends QuestionBoxTestSupport {

    @Autowired
    private CurriculumFacade curriculumFacade;

    private long boxQuestion(Fixture f, MockHttpSession who, String body) throws Exception {
        return json(send(who, f.school(), post(f.base() + "/questions"), body).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    /** سؤالِ چهارگزینه‌ای در بانکِ خودِ مدرسه (قابل‌مشاهده فقط برای همان مدرسه). */
    private long bankQuestion(long school, String text) {
        long topic = curriculumFacade.createTopic(school, null, "سرفصل " + text, 0).value();
        return curriculumFacade.createQuestion(school,
                new NewQuestion(topic, text, "الف", "ب", "ج", "د", 1, "پاسخ بانک", null, null)).value();
    }

    /** بانک سؤال (CURRICULUM) پولی است و با ساخت مدرسه فعال نمی‌شود. */
    private void entitleBank(Fixture f) {
        iamFacade.setSchoolEntitlement(f.school(), new com.example.roshdeandishe.iam.IamFacade.ModuleCode("CURRICULUM"), true);
    }

    private static String item(String source, long id) {
        return "{\"source\":\"" + source + "\",\"questionId\":" + id + "}";
    }

    private static String section(String title, String... items) {
        return "{\"title\":\"" + title + "\",\"instruction\":\"توضیح\",\"items\":[" + String.join(",", items) + "]}";
    }

    private static String sheet(String title, Long headerId, String... sections) {
        return "{\"title\":\"" + title + "\",\"headerId\":" + headerId + ",\"headerValues\":{\"DATE\":\"۱۴۰۵/۰۷/۲۱\"},"
                + "\"showEssayAnswerSpace\":true,\"includeAnswerKey\":false,\"sections\":["
                + String.join(",", sections) + "]}";
    }

    private long createSheet(Fixture f, MockHttpSession who, String body) throws Exception {
        return json(send(who, f.school(), post(f.base() + "/sheets"), body).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode resolved(Fixture f, MockHttpSession who, long sheetId) throws Exception {
        return json(send(who, f.school(), get(f.base() + "/sheets/" + sheetId + "/resolved"), null)
                .andExpect(status().isOk()));
    }

    @Test
    void saveSheetWithTwoSectionsMixingBoxAndBank() throws Exception {
        Fixture f = fixture();
        entitleBank(f);
        long mcq = boxQuestion(f, f.teacher1(), mcq("تستی باکس"));
        long essay = boxQuestion(f, f.teacher1(), essay("تشریحی باکس"));
        long bank = bankQuestion(f.school(), "سؤال بانک");
        long id = createSheet(f, f.teacher1(), sheet("آزمون میان‌ترم", null,
                section("بخش اول: تستی", item("BOX", mcq), item("BANK", bank)),
                section("بخش دوم: تشریحی", item("BOX", essay))));
        JsonNode r = resolved(f, f.teacher2(), id);
        assertThat(r.get("title").asText()).isEqualTo("آزمون میان‌ترم");
        assertThat(r.get("sections")).hasSize(2);
        JsonNode bankItem = r.get("sections").get(0).get("items").get(1);
        assertThat(bankItem.get("source").asText()).isEqualTo("BANK");
        assertThat(bankItem.get("choices")).hasSize(4);
        assertThat(bankItem.get("available").asBoolean()).isTrue();
        assertThat(r.get("sections").get(1).get("items").get(0).get("type").asText()).isEqualTo("ESSAY");
    }

    @Test
    void resolvedNumbersContinueAcrossSections() throws Exception {
        Fixture f = fixture();
        long a = boxQuestion(f, f.teacher1(), essay("الف"));
        long b = boxQuestion(f, f.teacher1(), essay("ب"));
        long c = boxQuestion(f, f.teacher1(), essay("ج"));
        long id = createSheet(f, f.teacher1(), sheet("شماره‌گذاری", null,
                section("اول", item("BOX", a), item("BOX", b)), section("دوم", item("BOX", c))));
        JsonNode r = resolved(f, f.teacher1(), id);
        assertThat(r.get("sections").get(0).get("items").get(0).get("number").asInt()).isEqualTo(1);
        assertThat(r.get("sections").get(0).get("items").get(1).get("number").asInt()).isEqualTo(2);
        assertThat(r.get("sections").get(1).get("items").get(0).get("number").asInt()).isEqualTo(3);
    }

    @Test
    void boxQuestionOfOtherSchoolRejected() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long foreign = boxQuestion(a, a.teacher1(), essay("مال مدرسه‌ی الف"));
        send(b.teacher1(), b.school(), post(b.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BOX", foreign)))).andExpect(status().isBadRequest());
    }

    @Test
    void incompleteImageOnlyQuestionRejected() throws Exception {
        Fixture f = fixture();
        long q = boxQuestion(f, f.teacher1(),
                "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"IMAGE_ONLY\",\"correctChoice\":1}");
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BOX", q)))).andExpect(status().isBadRequest());
    }

    @Test
    void bankQuestionNotVisibleToSchoolRejected() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        entitleBank(b);
        long foreignBank = bankQuestion(a.school(), "بانک مدرسه الف");
        send(b.teacher1(), b.school(), post(b.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BANK", foreignBank)))).andExpect(status().isBadRequest());
    }

    @Test
    void softDeletedBoxQuestionStillResolvesInSavedSheet() throws Exception {
        Fixture f = fixture();
        long q = boxQuestion(f, f.teacher1(), essay("برای حذف"));
        String body = sheet("برگه", null, section("بخش", item("BOX", q)));
        long id = createSheet(f, f.teacher1(), body);
        send(f.teacher1(), f.school(), delete(f.base() + "/questions/" + q), null).andExpect(status().isNoContent());
        JsonNode item = resolved(f, f.teacher1(), id).get("sections").get(0).get("items").get(0);
        assertThat(item.get("available").asBoolean()).isTrue();
        assertThat(item.get("text").asText()).isEqualTo("برای حذف");
        send(f.teacher1(), f.school(), put(f.base() + "/sheets/" + id), body).andExpect(status().isOk());
    }

    @Test
    void deletedBoxQuestionNotAddableToSheet() throws Exception {
        Fixture f = fixture();
        long q = boxQuestion(f, f.teacher1(), essay("حذف‌شده"));
        send(f.teacher1(), f.school(), delete(f.base() + "/questions/" + q), null).andExpect(status().isNoContent());
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BOX", q)))).andExpect(status().isBadRequest());
    }

    @Test
    void unavailableBankQuestionResolvesAsPlaceholder() throws Exception {
        Fixture f = fixture();
        entitleBank(f);
        long bank = bankQuestion(f.school(), "بانک گذرا");
        long id = createSheet(f, f.teacher1(), sheet("برگه", null, section("بخش", item("BANK", bank))));
        jdbc.update("delete from curriculum.questions where id = ?", bank);
        JsonNode item = resolved(f, f.teacher1(), id).get("sections").get(0).get("items").get(0);
        assertThat(item.get("available").asBoolean()).isFalse();
        assertThat(item.get("number").asInt()).isEqualTo(1);
    }

    @Test
    void positionsAreNormalisedOnSave() throws Exception {
        Fixture f = fixture();
        long a = boxQuestion(f, f.teacher1(), essay("الف"));
        long b = boxQuestion(f, f.teacher1(), essay("ب"));
        long c = boxQuestion(f, f.teacher1(), essay("ج"));
        long id = createSheet(f, f.teacher1(), sheet("برگه", null,
                section("یک", item("BOX", a), item("BOX", b), item("BOX", c)), section("دو"), section("سه", item("BOX", a))));
        send(f.teacher1(), f.school(), put(f.base() + "/sheets/" + id),
                sheet("برگه", null, section("سه", item("BOX", c)), section("یک", item("BOX", b), item("BOX", a))))
                .andExpect(status().isOk());
        List<Integer> sectionPositions = jdbc.queryForList(
                "select position from questionbox.sheet_sections where sheet_id = ? order by position", Integer.class, id);
        assertThat(sectionPositions).containsExactly(1, 2);
        JsonNode r = resolved(f, f.teacher1(), id);
        assertThat(r.get("sections").get(0).get("title").asText()).isEqualTo("سه");
        assertThat(r.get("sections").get(1).get("items").get(0).get("text").asText()).isEqualTo("ب");
        List<Integer> itemPositions = jdbc.queryForList("""
                select i.position from questionbox.sheet_items i
                join questionbox.sheet_sections s on s.id = i.section_id
                where s.sheet_id = ? and s.position = 2 order by i.position
                """, Integer.class, id);
        assertThat(itemPositions).containsExactly(1, 2);
    }

    @Test
    void sheetNeedsASectionWithATitle() throws Exception {
        Fixture f = fixture();
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"), sheet("بدون بخش", null))
                .andExpect(status().isBadRequest());
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"), sheet("بخش بی‌نام", null, section("  ")))
                .andExpect(status().isBadRequest());
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"), sheet("  ", null, section("بخش")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headerMustBeUsableAndInSchool() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long imageHeader = json(send(a.teacher1(), a.school(), post(a.base() + "/headers"),
                "{\"name\":\"تصویری\",\"mode\":\"IMAGE\"}").andExpect(status().isCreated())).get("id").asLong();
        send(a.teacher1(), a.school(), post(a.base() + "/sheets"), sheet("x", imageHeader, section("بخش")))
                .andExpect(status().isBadRequest());
        long layoutHeader = json(send(a.teacher1(), a.school(), post(a.base() + "/headers"),
                "{\"name\":\"قالب\",\"mode\":\"LAYOUT\",\"layout\":\"BOXED\",\"fields\":[\"TITLE\"]}")
                .andExpect(status().isCreated())).get("id").asLong();
        send(b.teacher1(), b.school(), post(b.base() + "/sheets"), sheet("x", layoutHeader, section("بخش")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headerInUseCannotBeDeleted() throws Exception {
        Fixture f = fixture();
        long header = json(send(f.teacher1(), f.school(), post(f.base() + "/headers"),
                "{\"name\":\"قالب\",\"mode\":\"LAYOUT\",\"layout\":\"CLASSIC\",\"fields\":[\"TITLE\"]}")
                .andExpect(status().isCreated())).get("id").asLong();
        long q = boxQuestion(f, f.teacher1(), essay("س"));
        long sheetId = createSheet(f, f.teacher1(), sheet("برگه", header, section("بخش", item("BOX", q))));
        send(f.teacher1(), f.school(), delete(f.base() + "/headers/" + header), null).andExpect(status().isBadRequest());
        JsonNode r = resolved(f, f.teacher1(), sheetId);
        assertThat(r.get("header").get("id").asLong()).isEqualTo(header);
        assertThat(r.get("headerValues").get("DATE").asText()).isEqualTo("۱۴۰۵/۰۷/۲۱");
    }

    @Test
    void otherSchoolSheetIs404() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long q = boxQuestion(a, a.teacher1(), essay("س"));
        long id = createSheet(a, a.teacher1(), sheet("برگه", null, section("بخش", item("BOX", q))));
        send(b.admin(), b.school(), get(b.base() + "/sheets/" + id), null).andExpect(status().isNotFound());
        send(b.admin(), b.school(), get(b.base() + "/sheets/" + id + "/resolved"), null).andExpect(status().isNotFound());
        send(b.admin(), b.school(), delete(b.base() + "/sheets/" + id), null).andExpect(status().isNotFound());
        assertThat(json(send(b.admin(), b.school(), get(b.base() + "/sheets"), null))).isEmpty();
    }

    @Test
    void editRulesMatchQuestionsAndListShowsSummaries() throws Exception {
        Fixture f = fixture();
        long q = boxQuestion(f, f.teacher1(), essay("س"));
        String body = sheet("برگه معلم یک", null, section("بخش", item("BOX", q)));
        long id = createSheet(f, f.teacher1(), body);
        send(f.teacher2(), f.school(), put(f.base() + "/sheets/" + id), body).andExpect(status().isForbidden());
        send(f.teacher2(), f.school(), delete(f.base() + "/sheets/" + id), null).andExpect(status().isForbidden());
        send(f.teacher2(), f.school(), get(f.base() + "/sheets"), null)
                .andExpect(jsonPath("$[0].title").value("برگه معلم یک"))
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[0].itemCount").value(1));
        send(f.admin(), f.school(), delete(f.base() + "/sheets/" + id), null).andExpect(status().isNoContent());
        send(f.admin(), f.school(), get(f.base() + "/sheets/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void bankItemsNeedTheCurriculumEntitlement() throws Exception {
        Fixture f = fixture();
        long bank = bankQuestion(f.school(), "بانک پولی");
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BANK", bank)))).andExpect(status().isBadRequest());
        entitleBank(f);
        send(f.teacher1(), f.school(), post(f.base() + "/sheets"),
                sheet("x", null, section("بخش", item("BANK", bank)))).andExpect(status().isCreated());
    }

    @Test
    void anImageOnlyQuestionThatLostItsStemImageResolvesAsUnavailable() throws Exception {
        Fixture f = fixture();
        long q = boxQuestion(f, f.teacher1(), "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"IMAGE_ONLY\",\"correctChoice\":1}");
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
        long image = json(mockMvc.perform(multipart(f.base() + "/questions/" + q + "/images")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", png))
                .param("role", "STEM")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("X-School-ID", String.valueOf(f.school())).session(f.teacher1()))
                .andExpect(status().isCreated())).get("id").asLong();
        long id = createSheet(f, f.teacher1(), sheet("برگه", null, section("بخش", item("BOX", q))));
        assertThat(resolved(f, f.teacher1(), id).get("sections").get(0).get("items").get(0).get("available").asBoolean()).isTrue();

        send(f.teacher1(), f.school(), delete(f.base() + "/questions/" + q + "/images/" + image), null)
                .andExpect(status().isNoContent());

        JsonNode item = resolved(f, f.teacher1(), id).get("sections").get(0).get("items").get(0);
        assertThat(item.get("available").asBoolean()).isFalse();
    }
}
