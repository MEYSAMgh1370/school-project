package com.example.roshdeandishe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * انتخابگر پایه و درس (course-picker.js): فهرست درس‌های یک پایه با رشته و topicId، برای این‌که صفحه‌های
 * سرفصل/آزمون/برنامه فقط زیر‌درختِ درسِ انتخاب‌شده را بخوانند. مدیر پلتفرم درس‌ها را با topicId می‌سازد؛
 * مدیر مدرسه این فهرست را می‌خواند.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=course-catalog-flow@roshdeandishe.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourseCatalogFlowIntegrationTest {

    private static final String PLATFORM_EMAIL = "course-catalog-flow@roshdeandishe.test";
    private static final String COURSES = "/api/platform/academic/courses";
    private static final String CATALOG = "/api/admin/schools/1/courses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from academic.courses where name like ?", "CCF-" + tag + "%");
        jdbc.update("delete from iam.platform_admins");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private void registerIfAbsent(String email) throws Exception {
        Integer existing = jdbc.queryForObject("select count(*) from iam.users where email = ?", Integer.class, email);
        if (existing != null && existing > 0) {
            return;
        }
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    /** کاربر پس از بالا آمدن اپ ثبت‌نام می‌کند، پس provisioner باید دوباره اجرا شود. */
    private MockHttpSession platformAdminSession() throws Exception {
        registerIfAbsent(PLATFORM_EMAIL);
        ApplicationRunner provisioner = runners.stream()
                .filter(r -> AopUtils.getTargetClass(r).getSimpleName().equals("PlatformAdminProvisioner"))
                .findFirst()
                .orElseThrow();
        provisioner.run(new DefaultApplicationArguments());
        return login(PLATFORM_EMAIL, "password123");
    }

    private void upsertCourse(MockHttpSession platform, int gradeLevel, String name, String track, Long topicId)
            throws Exception {
        String trackJson = track == null ? "null" : "\"" + track + "\"";
        String topicJson = topicId == null ? "null" : String.valueOf(topicId);
        mockMvc.perform(post(COURSES)
                        .with(csrf())
                        .session(platform)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gradeLevel\":" + gradeLevel + ",\"name\":\"" + name + "\","
                                + "\"track\":" + trackJson + ",\"topicId\":" + topicJson + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void schoolAdminSeesCoursesOfTheGradeWithTrackAndTopic() throws Exception {
        MockHttpSession platform = platformAdminSession();
        upsertCourse(platform, 11, "CCF-" + tag + "-مشترک", null, 9001L);
        upsertCourse(platform, 11, "CCF-" + tag + "-فیزیک", "تجربی", 9002L);
        upsertCourse(platform, 11, "CCF-" + tag + "-بی‌سرفصل", null, null);
        upsertCourse(platform, 12, "CCF-" + tag + "-پایه‌ی دیگر", null, 9003L);

        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        mockMvc.perform(get(CATALOG)
                        .param("gradeLevel", "11")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CCF-" + tag + "-مشترک' && @.topicId == 9001 && @.track == null)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CCF-" + tag + "-فیزیک' && @.topicId == 9002 && @.track == 'تجربی')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CCF-" + tag + "-بی‌سرفصل' && @.topicId == null)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CCF-" + tag + "-پایه‌ی دیگر')]").doesNotExist());
    }

    @Test
    void gradeOutsideOneToTwelveIsBadRequest() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(get(CATALOG)
                        .param("gradeLevel", "13")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isBadRequest());
    }
}
