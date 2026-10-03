package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * سال تحصیلی جاریِ مدرسه (openspec/changes/academic-year.md، فاز ۰) روی Postgres
 * واقعی: مدیر فهرست همه‌ی سال‌ها را می‌بیند و سال جاری را از آن انتخاب می‌کند؛
 * بقیه‌ی اعضا فقط «سال جاری» را می‌بینند (بدون فهرست) و نمی‌توانند چیزی تغییر
 * دهند؛ بیگانه چیزی نمی‌بیند. سال‌ها را خودِ برنامه می‌سازد
 * (AcademicYearProvisioner)، نه مدیر و نه اپراتور.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AcademicYearFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
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

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createClass(MockHttpSession adminSession, String name, String gradingScheme) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"" + gradingScheme + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long enrollStudent(MockHttpSession adminSession, String email, String fullName, long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
    }

    private long yearId(String code) {
        return jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, code);
    }

    /** سالِ جاریِ مدرسه‌ی ۱ در دیتابیس (null = هنوز انتخاب نشده). */
    private Long currentYearId() {
        return jdbc.queryForObject("select current_academic_year_id from school.schools where id = 1", Long.class);
    }

    private String body(long yearId, Long expectedCurrent) {
        return "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":" + expectedCurrent + "}";
    }

    /** انتخابِ سال با «سالِ جاریِ مورد انتظار» = همان چیزی که الان در دیتابیس است. */
    private void selectYear(MockHttpSession session, long yearId, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, currentYearId())))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void adminSeesAllYearsAndPicksAnOpenOneAndEveryMemberSeesOnlyTheCurrentYear() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long chosen = yearId("1405-1406");

        mockMvc.perform(get("/api/admin/schools/1/academic-year")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years[?(@.code == '1404-1405')]").exists())
                .andExpect(jsonPath("$.years[?(@.code == '1405-1406')]").exists());

        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(chosen, currentYearId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.id").value(chosen))
                .andExpect(jsonPath("$.current.code").value("1405-1406"));

        // یک عضوِ عادی (دانش‌آموز): فقط سال جاری، بدون فهرست سال‌ها
        long classId = createClass(admin, "پایه پنجم - سال تحصیلی", "NUMERIC");
        String studentEmail = "academicyear.student@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز سال تحصیلی");
        enrollStudent(admin, studentEmail, "دانش‌آموز سال تحصیلی", classId);

        mockMvc.perform(get("/api/me/schools/1/academic-year").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.code").value("1405-1406"))
                .andExpect(jsonPath("$.years").doesNotExist());
    }

    @Test
    void aMemberWithoutTheManagePermissionCannotReadTheYearListOrChangeTheYear() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - سال تحصیلی بی‌مجوز", "NUMERIC");
        String studentEmail = "academicyear.nopermission@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز بی‌مجوز");
        enrollStudent(admin, studentEmail, "دانش‌آموز بی‌مجوز", classId);

        mockMvc.perform(get("/api/admin/schools/1/academic-year")
                        .header("X-School-ID", "1")
                        .session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
        selectYear(student, yearId("1404-1405"), 403);
    }

    @Test
    void aYearThatDoesNotExistYetCannotBeChosenAndNothingChanges() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long before = yearId("1404-1405");
        selectYear(admin, before, 200);

        // سالِ آینده تا تاریخِ ساخته‌شدنش (کانفیگ) در فهرست نیست: شناسه‌ی ناموجود = 404
        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999999L, currentYearId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("academic-year-not-found")));

        // انتخابِ ردشده هیچ تغییری نداده: سال جاری همان قبلی است
        mockMvc.perform(get("/api/admin/schools/1/academic-year")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(jsonPath("$.current.id").value(before));
    }

    @Test
    void theApplicationCreatesTheAcademicYearsItselfWithoutGapsOrDuplicates() {
        // AcademicYearProvisioner هنگام بالا آمدن اجرا شده: سال‌ها از سالِ پایه (1404)
        // تا سالِ تحصیلیِ امروز پشت‌سرهم و بدون تکرار ساخته شده‌اند، نه با دست.
        // (بعد از ۱ مرداد ۱۴۰۵ حداقل 1405-1406 هم هست.)
        Integer count = jdbc.queryForObject("select count(*) from school.academic_years", Integer.class);
        Integer min = jdbc.queryForObject("select min(start_year) from school.academic_years", Integer.class);
        Integer max = jdbc.queryForObject("select max(start_year) from school.academic_years", Integer.class);

        assertThat(min).isEqualTo(1404);
        assertThat(max).isGreaterThanOrEqualTo(1405);
        assertThat(count).isEqualTo(max - min + 1);
        assertThat(jdbc.queryForObject(
                "select count(*) from school.academic_years where code <> start_year || '-' || (start_year + 1)",
                Integer.class)).isZero();
    }

    @Test
    void aStaleExpectedCurrentYearIsRejectedSoAnOlderChoiceCannotRevertANewerOne() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long first = yearId("1404-1405");
        long second = yearId("1405-1406");
        selectYear(admin, first, 200);

        // دو درخواست با همان «سالِ جاریِ مورد انتظار» (first) ولی دو انتخابِ متفاوت، مثل
        // A←B←A در مرورگر: اولی (انتخابِ second) می‌نویسد؛ دومی که هنوز first را «جاری»
        // می‌داند باید رد شود، نه اینکه ترتیبِ commit انتخابِ اول را برگرداند
        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(second, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.id").value(second));

        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(first, first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("academic-year-changed")));

        assertThat(currentYearId()).isEqualTo(second);
    }

    @Test
    void aSchoolThatHasNotChosenYetIsChosenWithANullExpectation() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        jdbc.update("update school.schools set current_academic_year_id = null where id = 1");

        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId("1404-1405"), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.code").value("1404-1405"));

        // «هنوز انتخاب نشده» دیگر درست نیست: همان انتظارِ null حالا کهنه است
        mockMvc.perform(put("/api/admin/schools/1/academic-year")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId("1405-1406"), null)))
                .andExpect(status().isConflict());
    }

    @Test
    void twoConcurrentChoicesWithTheSameExpectationLetExactlyOneWin() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long first = yearId("1404-1405");
        long second = yearId("1405-1406");
        // هر دو درخواست «هنوز انتخاب نشده» را انتظار دارند و دو سالِ متفاوت می‌خواهند:
        // هرکدام اول برسد می‌نویسد و دیگری باید ۴۰۹ بگیرد، نه این‌که ترتیبِ commit تعیین کند
        jdbc.update("update school.schools set current_academic_year_id = null where id = 1");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (long target : new long[]{first, second}) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(put("/api/admin/schools/1/academic-year")
                                    .with(csrf()).header("X-School-ID", "1").session(admin)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body(target, null)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            java.util.List<Integer> statuses = new java.util.ArrayList<>();
            for (var result : results) {
                statuses.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(currentYearId()).isIn(first, second);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aStrangerWhoIsNotAMemberCannotReadTheSchoolsCurrentYear() throws Exception {
        MockHttpSession stranger = registerAndLogin("academicyear.stranger@roshdeandishe.test", "کاربر بی‌ربط");

        mockMvc.perform(get("/api/me/schools/1/academic-year").session(stranger))
                .andExpect(status().is4xxClientError());
    }
}
