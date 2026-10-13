package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * صفحه‌های باکس سؤالات پشت ورود‌اند، بدون X-School-ID بارگذاری می‌شوند و در سایدبار هستند (هیچ URL یتیمی).
 */
class QuestionBoxPagesFlowIntegrationTest extends QuestionBoxTestSupport {

    private String page(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    @Test
    void unauthenticatedVisitorsAreRedirectedToLogin() throws Exception {
        for (String path : new String[]{"/question-box", "/question-sheets", "/question-sheets/1/print"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
        }
    }

    @Test
    void thePagesRenderTheirShellForATeacherWithoutASchoolHeaderAndAreInTheSidebar() throws Exception {
        Fixture f = fixture();

        String box = page(f.teacher1(), "/question-box");
        assertThat(box).contains("x-data=\"questionBoxPage()\"", "افزودن سؤال", "/js/question-box.js");
        assertThat(box).contains("href=\"/question-box\"", "href=\"/question-sheets\"");

        String sheets = page(f.teacher1(), "/question-sheets");
        assertThat(sheets).contains("x-data=\"questionSheetsPage()\"", "ذخیره‌ی برگه", "/js/question-sheets.js");

        String print = page(f.teacher1(), "/question-sheets/42/print");
        assertThat(print).contains("x-data=\"questionSheetPrintPage()\"", "data-sheet-id=\"42\"", "data-school-id=\"" + f.school() + "\"");
        assertThat(print).doesNotContain("schoolNavbar");
    }
}
