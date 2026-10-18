package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * نمای «دانش‌آموزمحور» صفحه‌ی مالی (school-settings، وظیفه ۵) روی Postgres واقعی. هر تست مدرسه‌ی تازه می‌سازد.
 * سناریوی مشترک: پنج دانش‌آموز، شهریه‌ی سالِ قبل ۸۰ و سالِ جاری ۱۰۰؛ فقط A هر دو سال را کامل پرداخته،
 * B ۳۰ از سالِ قبل، C هیچ، و D (ترک‌کرده) و E (فارغ‌التحصیل) فقط فاکتور سالِ قبل دارند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceStudentOverviewFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Fixture(long schoolId, MockHttpSession admin, long classId) {
    }

    private record Scenario(Fixture f, long a, long b, long c, long d, long e) {
    }

    private String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(Fixture f, MockHttpServletRequestBuilder builder, String body) throws Exception {
        MockHttpServletRequestBuilder request = builder.with(csrf()).header("X-School-ID", String.valueOf(f.schoolId()))
                .session(f.admin());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions getAdmin(Fixture f, String path) throws Exception {
        return mockMvc.perform(get("/api/admin/schools/" + f.schoolId() + path)
                .header("X-School-ID", String.valueOf(f.schoolId())).session(f.admin()));
    }

    /** پارامترها جفت‌جفت (نام، مقدار) و با param() تا نویسه‌های فارسی خودکار کدگذاری شوند. */
    private ResultActions students(Fixture f, String... pairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/schools/" + f.schoolId() + "/finance/students")
                .header("X-School-ID", String.valueOf(f.schoolId())).session(f.admin());
        for (int i = 0; i < pairs.length; i += 2) {
            request.param(pairs[i], pairs[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private Fixture newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("ov.founder"), "بنیان‌گذار نمای مالی");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی نمای مالی " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                        + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}")).andExpect(status().isOk())).get("id").asLong();
        Fixture noClass = new Fixture(schoolId, founder, 0);
        long classId = json(send(noClass, post("/api/admin/schools/" + schoolId + "/classes"),
                "{\"name\":\"پنجم\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}")
                .andExpect(status().isOk())).get("id").asLong();
        return new Fixture(schoolId, founder, classId);
    }

    private long enroll(Fixture f, String name) throws Exception {
        String email = unique("ov.s");
        registerAndLogin(email, name);
        send(f, post("/api/admin/schools/" + f.schoolId() + "/students"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"classId\":" + f.classId() + "}")
                .andExpect(status().isOk());
        return jdbc.queryForObject("select id from academic.students where school_id = ? and user_id = "
                + "(select id from iam.users where email = ?)", Long.class, f.schoolId(), email);
    }

    private void setCurrentYear(Fixture f, String code) throws Exception {
        long yearId = jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, code);
        Long expected = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, f.schoolId());
        send(f, put("/api/admin/schools/" + f.schoolId() + "/academic-year"),
                "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":" + expected + "}")
                .andExpect(status().isOk());
    }

    private void setTuition(Fixture f, String year, int amount) throws Exception {
        String query = year == null ? "" : "?academicYear=" + year;
        send(f, put("/api/admin/schools/" + f.schoolId() + "/tuition-fee" + query), "{\"amount\":" + amount + "}")
                .andExpect(status().isOk());
    }

    private void ensureInvoiced(Fixture f, String year, int expectedTotal) throws Exception {
        String query = year == null ? "" : "?academicYear=" + year;
        send(f, post("/api/admin/schools/" + f.schoolId() + "/invoices/backfill" + query), null).andExpect(status().isOk());
        int count = -1;
        for (int attempt = 0; attempt < 50; attempt++) {
            count = 0;
            for (JsonNode invoice : json(getAdmin(f, "/invoices" + query).andExpect(status().isOk()))) {
                if (year == null || (!invoice.get("academicYear").isNull()
                        && year.equals(invoice.get("academicYear").asText()))) {
                    count++;
                }
            }
            if (count == expectedTotal) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(count).as("invoices of " + year).isEqualTo(expectedTotal);
    }

    private long invoiceId(Fixture f, long studentId, String year) {
        return year == null
                ? jdbc.queryForObject("select id from finance.invoices where school_id = ? and student_id = ? "
                + "and academic_year is null", Long.class, f.schoolId(), studentId)
                : jdbc.queryForObject("select id from finance.invoices where school_id = ? and student_id = ? "
                + "and academic_year = ?", Long.class, f.schoolId(), studentId, year);
    }

    private void pay(Fixture f, long invoiceId, int amount) throws Exception {
        send(f, post("/api/admin/schools/" + f.schoolId() + "/invoices/" + invoiceId + "/payments"),
                "{\"amount\":" + amount + ",\"method\":\"CASH\",\"note\":null}").andExpect(status().isOk());
    }

    private void setStatus(long studentId, String status) {
        jdbc.update("update academic.students set status = ? where id = ?", status, studentId);
    }

    private Scenario scenario() throws Exception {
        Fixture f = newSchool();
        long a = enroll(f, "علی رضایی");
        long b = enroll(f, "سارا احمدی");
        long c = enroll(f, "رضا کریمی");
        long d = enroll(f, "علی کاظمی");
        long e = enroll(f, "مریم نوری");
        setCurrentYear(f, CURRENT);
        setTuition(f, OLD, 80);
        ensureInvoiced(f, OLD, 5);
        setStatus(d, "LEFT");
        setStatus(e, "GRADUATED");
        setTuition(f, null, 100);
        ensureInvoiced(f, CURRENT, 3);

        pay(f, invoiceId(f, a, OLD), 80);
        pay(f, invoiceId(f, a, CURRENT), 100);
        pay(f, invoiceId(f, b, OLD), 30);
        pay(f, invoiceId(f, e, OLD), 20);
        // سررسیدِ فاکتورِ سالِ قبلِ C گذشته
        jdbc.update("update finance.invoices set due_date = now() - interval '2 days' where id = ?",
                invoiceId(f, c, OLD));
        return new Scenario(f, a, b, c, d, e);
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("studentId").asLong()));
        return ids;
    }

    private static JsonNode row(JsonNode page, long studentId) {
        for (JsonNode item : page.get("items")) {
            if (item.get("studentId").asLong() == studentId) {
                return item;
            }
        }
        throw new AssertionError("student " + studentId + " not listed");
    }

    @Test
    void allScopeListsCurrentInvoiceAndPriorDebtors() throws Exception {
        Scenario s = scenario();
        JsonNode page = json(students(s.f()).andExpect(status().isOk()));

        assertThat(ids(page)).containsExactlyInAnyOrder(s.a(), s.b(), s.c(), s.d(), s.e());
        assertThat(page.get("total").asLong()).isEqualTo(5);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(50);
        assertThat(page.get("counts").get("all").asLong()).isEqualTo(5);
        assertThat(page.get("counts").get("current").asLong()).isEqualTo(3);
        assertThat(page.get("counts").get("previous").asLong()).isEqualTo(4);

        JsonNode a = row(page, s.a());
        assertThat(a.get("fullName").asText()).isEqualTo("علی رضایی");
        assertThat(a.get("studentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(a.get("currentStatus").asText()).isEqualTo("PAID");
        assertThat(a.get("currentRemaining").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("previousDebt").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("totalRemaining").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("overdue").asBoolean()).isFalse();

        JsonNode b = row(page, s.b());
        assertThat(b.get("currentStatus").asText()).isEqualTo("UNPAID");
        assertThat(b.get("currentRemaining").decimalValue()).isEqualByComparingTo("100");
        assertThat(b.get("previousDebt").decimalValue()).isEqualByComparingTo("50");
        assertThat(b.get("totalRemaining").decimalValue()).isEqualByComparingTo("150");

        JsonNode c = row(page, s.c());
        assertThat(c.get("previousDebt").decimalValue()).isEqualByComparingTo("80");
        assertThat(c.get("overdue").asBoolean()).isTrue();
    }

    @Test
    void previousScopeIsDebtorsOnly() throws Exception {
        Scenario s = scenario();
        JsonNode page = json(students(s.f(), "scope", "previous").andExpect(status().isOk()));

        // A تسویه‌ی کامل کرده و فهرست نمی‌شود؛ E (فارغ‌التحصیل) و D (ترک‌کرده) بدهکارند
        assertThat(ids(page)).containsExactlyInAnyOrder(s.b(), s.c(), s.d(), s.e());
        assertThat(page.get("total").asLong()).isEqualTo(4);
        // سازگاری با counts: همان فیلترها، فقط scope فرق می‌کند
        assertThat(page.get("counts").get("all").asLong()).isEqualTo(5);

        JsonNode current = json(students(s.f(), "scope", "current").andExpect(status().isOk()));
        assertThat(ids(current)).containsExactlyInAnyOrder(s.a(), s.b(), s.c());
        assertThat(current.get("total").asLong()).isEqualTo(3);
    }

    @Test
    void leftAndGraduatedDebtorsAreListed() throws Exception {
        Scenario s = scenario();
        // نامِ خالی (کاربرِ بی‌نام) → «نامشخص»
        jdbc.update("update iam.users set full_name = '' where id = (select user_id from academic.students where id = ?)",
                s.e());
        JsonNode page = json(students(s.f(), "scope", "previous").andExpect(status().isOk()));

        JsonNode d = row(page, s.d());
        assertThat(d.get("studentStatus").asText()).isEqualTo("LEFT");
        assertThat(d.get("fullName").asText()).isEqualTo("علی کاظمی");
        assertThat(d.get("currentStatus").isNull()).isTrue();
        assertThat(d.get("previousDebt").decimalValue()).isEqualByComparingTo("80");
        assertThat(d.get("currentRemaining").decimalValue()).isEqualByComparingTo("0");

        JsonNode e = row(page, s.e());
        assertThat(e.get("studentStatus").asText()).isEqualTo("GRADUATED");
        assertThat(e.get("fullName").asText()).isEqualTo("نامشخص");
        assertThat(e.get("previousDebt").decimalValue()).isEqualByComparingTo("60");
    }

    @Test
    void nullYearInvoiceCountsAsPreviousDebt() throws Exception {
        Fixture f = newSchool();
        long x = enroll(f, "نیکا صادقی");
        long y = enroll(f, "پویا امینی");
        setTuition(f, null, 100);
        ensureInvoiced(f, null, 2);
        pay(f, invoiceId(f, y, null), 100);

        // مدرسه هنوز سالِ جاری ندارد: همه‌ی فاکتورها «جاری»اند و بدهی قبلی صفر است
        JsonNode before = json(students(f).andExpect(status().isOk()));
        assertThat(ids(before)).containsExactlyInAnyOrder(x, y);
        assertThat(row(before, x).get("previousDebt").decimalValue()).isEqualByComparingTo("0");
        assertThat(row(before, x).get("currentStatus").asText()).isEqualTo("UNPAID");

        // با انتخاب سالِ جاری، فاکتورِ بدون سال می‌شود بدهیِ قبلی و هرگز حذف نمی‌شود
        setCurrentYear(f, CURRENT);
        JsonNode after = json(students(f).andExpect(status().isOk()));
        assertThat(ids(after)).containsExactly(x);
        JsonNode row = row(after, x);
        assertThat(row.get("previousDebt").decimalValue()).isEqualByComparingTo("100");
        assertThat(row.get("currentStatus").isNull()).isTrue();
        assertThat(row.get("totalRemaining").decimalValue()).isEqualByComparingTo("100");
        assertThat(after.get("counts").get("current").asLong()).isZero();
        assertThat(after.get("counts").get("previous").asLong()).isEqualTo(1);
    }

    @Test
    void schoolWithoutCurrentYearStillLists() throws Exception {
        Fixture f = newSchool();
        long x = enroll(f, "کیان رحیمی");
        setTuition(f, null, 90);
        ensureInvoiced(f, null, 1);

        JsonNode page = json(students(f, "scope", "current").andExpect(status().isOk()));
        assertThat(ids(page)).containsExactly(x);
        assertThat(row(page, x).get("currentRemaining").decimalValue()).isEqualByComparingTo("90");
        assertThat(row(page, x).get("previousDebt").decimalValue()).isEqualByComparingTo("0");
        assertThat(json(students(f, "scope", "previous").andExpect(status().isOk())).get("total").asLong()).isZero();
        assertThat(json(students(f).andExpect(status().isOk())).get("total").asLong()).isEqualTo(1);
    }

    @Test
    void countsFollowFiltersButNotScope() throws Exception {
        Scenario s = scenario();

        // فیلتر نام: «علی» → A و D
        JsonNode byName = json(students(s.f(), "scope", "current", "firstName", "علی").andExpect(status().isOk()));
        assertThat(ids(byName)).containsExactly(s.a());
        assertThat(byName.get("total").asLong()).isEqualTo(1);
        assertThat(byName.get("counts").get("all").asLong()).isEqualTo(2);
        assertThat(byName.get("counts").get("current").asLong()).isEqualTo(1);
        assertThat(byName.get("counts").get("previous").asLong()).isEqualTo(1);

        // حرف عربی در نام‌خانوادگی
        JsonNode arabic = json(students(s.f(), "lastName", "کريمي").andExpect(status().isOk()));
        assertThat(ids(arabic)).containsExactly(s.c());

        // جستجوی عمومی
        assertThat(ids(json(students(s.f(), "q", "ضای").andExpect(status().isOk())))).containsExactly(s.a());

        // فیلتر بدهی روی وضعیتِ کلی
        JsonNode owing = json(students(s.f(), "debt", "owing").andExpect(status().isOk()));
        assertThat(ids(owing)).containsExactlyInAnyOrder(s.b(), s.c(), s.d(), s.e());
        assertThat(owing.get("counts").get("all").asLong()).isEqualTo(4);
        assertThat(owing.get("counts").get("current").asLong()).isEqualTo(2);
        assertThat(owing.get("counts").get("previous").asLong()).isEqualTo(4);
        assertThat(ids(json(students(s.f(), "debt", "overdue").andExpect(status().isOk())))).containsExactly(s.c());
        assertThat(ids(json(students(s.f(), "debt", "settled").andExpect(status().isOk())))).containsExactly(s.a());

        // نام و بدهی با هم (AND)
        JsonNode both = json(students(s.f(), "firstName", "علی", "debt", "owing").andExpect(status().isOk()));
        assertThat(ids(both)).containsExactly(s.d());
        assertThat(both.get("counts").get("current").asLong()).isZero();
    }

    @Test
    void pagingAndSizeBounds() throws Exception {
        Scenario s = scenario();

        JsonNode first = json(students(s.f(), "size", "2", "page", "0").andExpect(status().isOk()));
        JsonNode second = json(students(s.f(), "size", "2", "page", "1").andExpect(status().isOk()));
        JsonNode third = json(students(s.f(), "size", "2", "page", "2").andExpect(status().isOk()));
        assertThat(first.get("items")).hasSize(2);
        assertThat(second.get("items")).hasSize(2);
        assertThat(third.get("items")).hasSize(1);
        assertThat(first.get("total").asLong()).isEqualTo(5);
        assertThat(third.get("page").asInt()).isEqualTo(2);
        assertThat(third.get("size").asInt()).isEqualTo(2);
        List<Long> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        all.addAll(ids(third));
        assertThat(all).doesNotHaveDuplicates().hasSize(5);
        assertThat(json(students(s.f(), "size", "2", "page", "9").andExpect(status().isOk())).get("items")).isEmpty();

        for (String[] bad : new String[][]{{"size", "101"}, {"size", "0"}, {"page", "-1"},
                {"scope", "bogus"}, {"debt", "bogus"}}) {
            students(s.f(), bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-filter")));
        }
        students(s.f(), "size", "100").andExpect(status().isOk());
        students(s.f(), "scope", "ALL", "debt", "Owing").andExpect(status().isOk());
    }

    @Test
    void parentGetsForbidden() throws Exception {
        Scenario s = scenario();
        String parentEmail = unique("ov.parent");
        MockHttpSession parent = registerAndLogin(parentEmail, "ولی نمای مالی");
        send(s.f(), post("/api/admin/schools/" + s.f().schoolId() + "/memberships"),
                "{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی نمای مالی\",\"roleCode\":\"PARENT\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/" + s.f().schoolId() + "/finance/students")
                        .header("X-School-ID", String.valueOf(s.f().schoolId())).session(parent))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/schools/" + s.f().schoolId() + "/finance/students/" + s.b() + "/invoices")
                        .header("X-School-ID", String.valueOf(s.f().schoolId())).session(parent))
                .andExpect(status().isForbidden());
    }

    @Test
    void invoicesOfStudentSpanAllYears() throws Exception {
        Scenario s = scenario();

        JsonNode invoices = json(getAdmin(s.f(), "/finance/students/" + s.b() + "/invoices").andExpect(status().isOk()));
        assertThat(invoices).hasSize(2);
        assertThat(invoices.get(0).get("academicYear").asText()).isEqualTo(CURRENT);
        assertThat(invoices.get(1).get("academicYear").asText()).isEqualTo(OLD);
        assertThat(invoices.get(1).get("amountPaid").decimalValue()).isEqualByComparingTo("30");
        invoices.forEach(i -> assertThat(i.get("studentId").asLong()).isEqualTo(s.b()));

        // دانش‌آموزِ فقط-سالِ-قبل
        assertThat(json(getAdmin(s.f(), "/finance/students/" + s.d() + "/invoices").andExpect(status().isOk()))).hasSize(1);

        // دانش‌آموزِ مدرسه‌ی دیگر هرگز برنمی‌گردد
        Scenario other = scenario();
        assertThat(json(getAdmin(s.f(), "/finance/students/" + other.b() + "/invoices").andExpect(status().isOk())))
                .isEmpty();
    }
}
