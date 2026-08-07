package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SchoolSelectionWarningFlowIntegrationTest {

    private static final List<PageWarning> SCHOOL_SCOPED_PAGES = List.of(
            new PageWarning("/people", "برای مدیریت اعضا، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/roles", "برای مدیریت نقش‌ها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/classes", "برای مدیریت کلاس‌ها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/students", "برای مدیریت دانش‌آموزان، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/grades", "برای مدیریت نمرات، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/finance", "برای مدیریت امور مالی، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/expenses", "برای مدیریت هزینه‌ها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/curriculum", "برای مدیریت سرفصل‌ها و سؤال‌ها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/study-plans", "برای مدیریت برنامه‌های مطالعه، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/exams", "برای مدیریت آزمون‌ها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/my-invoices", "برای مشاهده‌ی فاکتورها، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/my-grades", "برای مشاهده‌ی نمرات، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/my-study-plan", "برای مشاهده‌ی برنامه‌ی مطالعه، یک مدرسه از سوییچر بالا انتخاب کنید."),
            new PageWarning("/my-exams", "برای مشاهده‌ی آزمون‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.")
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void schoolSelectionWarningsStayCloakedUntilAlpineInitializes() throws Exception {
        MockHttpSession session = loginAsAdmin();

        for (PageWarning page : SCHOOL_SCOPED_PAGES) {
            MvcResult result = mockMvc.perform(get(page.path()).session(session))
                    .andExpect(status().isOk())
                    .andReturn();
            String html = result.getResponse().getContentAsString();

            assertThat(html)
                    .as("school-selection warning on %s", page.path())
                    .contains(page.warning());
            assertThat(html)
                    .as("school-selection warning cloak on %s", page.path())
                    .containsPattern("<[^>]*\\bx-cloak(?=\\s|=|>)[^>]*>\\s*"
                            + Pattern.quote(page.warning()));
        }
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private record PageWarning(String path, String warning) {
    }
}
