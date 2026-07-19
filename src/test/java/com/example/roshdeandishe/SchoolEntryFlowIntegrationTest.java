package com.example.roshdeandishe;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * نشانی اختصاصی مدرسه (slug) و درِ ورودی برنددار /s/{slug} - Tier 1 از
 * تصمیم «دامنه‌ی اختصاصی مدرسه» (CLAUDE.md §11): قالب slug عمداً قالب یک
 * DNS label است تا در فاز ساب‌دامین بدون migration استفاده شود.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SchoolEntryFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolFacade schoolFacade;

    private MockHttpSession registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"بنیان‌گذار آزمون\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private String createSchoolJson(String name, String slugJsonValue) {
        return "{\"name\":\"" + name + "\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\","
                + "\"level\":\"ELEMENTARY\",\"slug\":" + slugJsonValue + "}";
    }

    @Test
    void createSchoolWithSlugThenPublicEntryPageShowsSchoolName() throws Exception {
        MockHttpSession session = registerAndLogin("slug-founder-" + System.nanoTime() + "@example.test");

        // slug با حروف بزرگ فرستاده می‌شود - سرور باید lowercase کند
        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی تیزهوشان", "\"Tizhooshan\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tizhooshan"));

        // صفحه‌ی عمومی، بدون هیچ session ای - نام مدرسه + CTA ورود +
        // ست‌شدن selectedSchoolId روی کلیک
        mockMvc.perform(get("/s/tizhooshan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("مدرسه‌ی تیزهوشان")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("selectedSchoolId")));
    }

    @Test
    void unknownSlugRendersNotFoundStateWith404() throws Exception {
        mockMvc.perform(get("/s/hich-madrese-i"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("مدرسه‌ای با این نشانی یافت نشد")));
    }

    @Test
    void suspendedSchoolIsNotAdvertised() throws Exception {
        SchoolId schoolId = schoolFacade.createSchool(
                "مدرسه‌ی معلق", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, "moallagh-" + System.nanoTime() % 100000);
        SchoolFacade.SchoolInfo info = schoolFacade.findSchool(schoolId).orElseThrow();
        schoolFacade.setSchoolStatus(schoolId, SchoolStatus.SUSPENDED);

        mockMvc.perform(get("/s/" + info.slug()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("مدرسه‌ای با این نشانی یافت نشد")));
    }

    @Test
    void duplicateSlugIsRejectedWithConflict() throws Exception {
        MockHttpSession session = registerAndLogin("slug-dup-" + System.nanoTime() + "@example.test");

        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی اول", "\"yekta-slug\"")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی دوم", "\"yekta-slug\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("slug-already-taken")));
    }

    @Test
    void invalidAndReservedSlugsAreRejected() throws Exception {
        MockHttpSession session = registerAndLogin("slug-bad-" + System.nanoTime() + "@example.test");

        // قالب نامعتبر: خط تیره در ابتدا
        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی بد", "\"-bad-slug\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-slug")));

        // کوتاه‌تر از ۳ نویسه
        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی بد", "\"ab\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-slug")));

        // نام رزروشده (زیرساخت فاز ساب‌دامین)
        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی بد", "\"www\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-slug")));
    }

    @Test
    void slugIsOptionalAndOmittingItStillCreatesSchool() throws Exception {
        MockHttpSession session = registerAndLogin("slug-none-" + System.nanoTime() + "@example.test");

        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی بی‌نشانی", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").isEmpty());
    }

    @Test
    void authenticatedVisitorSeesDashboardCtaInsteadOfLogin() throws Exception {
        MockHttpSession session = registerAndLogin("slug-authed-" + System.nanoTime() + "@example.test");

        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSchoolJson("مدرسه‌ی احرازشده", "\"ehraz-shode\"")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/s/ehraz-shode").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ادامه به داشبورد")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")));
    }
}
