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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * شهریه و فاکتور به‌تفکیک سال (openspec/changes/academic-year.md، فاز ۲) روی Postgres واقعی. هر تست
 * مدرسه‌ی تازه‌ی خودش را می‌سازد (بنیان‌گذار = SCHOOL_ADMIN با FINANCE_* و ACADEMIC_YEAR_MANAGE، و ماژول‌های
 * رایگان ACADEMIC و FINANCE)، پس با دیتای بقیه‌ی تست‌ها هم‌پوشانی ندارد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancePerYearFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Fixture(long schoolId, MockHttpSession admin, long classId) {
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

    private Fixture newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("fy.founder"), "بنیان‌گذار مالی سالانه");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی مالی سالانه " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                        + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}")).andExpect(status().isOk())).get("id").asLong();
        Fixture noClass = new Fixture(schoolId, founder, 0);
        long classId = json(send(noClass, post("/api/admin/schools/" + schoolId + "/classes"),
                "{\"name\":\"پنجم\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}")
                .andExpect(status().isOk())).get("id").asLong();
        return new Fixture(schoolId, founder, classId);
    }

    private MockHttpSession enroll(Fixture f, String email, String name) throws Exception {
        MockHttpSession student = registerAndLogin(email, name);
        send(f, post("/api/admin/schools/" + f.schoolId() + "/students"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"classId\":" + f.classId() + "}")
                .andExpect(status().isOk());
        return student;
    }

    private void setCurrentYear(Fixture f, String code) throws Exception {
        long yearId = jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, code);
        Long expected = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, f.schoolId());
        send(f, put("/api/admin/schools/" + f.schoolId() + "/academic-year"),
                "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":" + expected + "}")
                .andExpect(status().isOk());
    }

    private ResultActions setTuition(Fixture f, String year, int amount) throws Exception {
        String query = year == null ? "" : "?academicYear=" + year;
        return send(f, put("/api/admin/schools/" + f.schoolId() + "/tuition-fee" + query), "{\"amount\":" + amount + "}");
    }

    private ResultActions backfill(Fixture f, String year) throws Exception {
        String query = year == null ? "" : "?academicYear=" + year;
        return send(f, post("/api/admin/schools/" + f.schoolId() + "/invoices/backfill" + query), null);
    }

    private int invoiceCount(Fixture f, String year, String expectedYearOrNull, boolean expectAllThatYear) throws Exception {
        String query = year == null ? "" : "?academicYear=" + year;
        JsonNode list = json(getAdmin(f, "/invoices" + query).andExpect(status().isOk()));
        int count = 0;
        for (JsonNode invoice : list) {
            if (expectAllThatYear) {
                assertThat(invoice.get("academicYear").isNull() ? null : invoice.get("academicYear").asText())
                        .isEqualTo(expectedYearOrNull);
            }
            count++;
        }
        return count;
    }

    @Test
    void aSchoolWithoutACurrentYearKeepsWorkingYearLess() throws Exception {
        Fixture f = newSchool();
        enroll(f, unique("fy.s1"), "دانش‌آموز یک");
        enroll(f, unique("fy.s2"), "دانش‌آموز دو");

        setTuition(f, null, 100).andExpect(status().isOk()).andExpect(jsonPath("$.academicYear").doesNotExist());
        getAdmin(f, "/tuition-fee").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(100));
        backfill(f, null).andExpect(status().isOk()).andExpect(jsonPath("$.issuedCount").value(2));
        backfill(f, null).andExpect(status().isOk()).andExpect(jsonPath("$.issuedCount").value(0));
        assertThat(invoiceCount(f, null, null, true)).isEqualTo(2);
        getAdmin(f, "/finance/unattributed").andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionFee").value(true)).andExpect(jsonPath("$.invoices").value(2));
    }

    @Test
    void tuitionAndInvoicesArePerYearAndANewYearStartsWithoutTuition() throws Exception {
        Fixture f = newSchool();
        setCurrentYear(f, CURRENT);
        enroll(f, unique("fy.a"), "دانش‌آموز الف");
        enroll(f, unique("fy.b"), "دانش‌آموز ب");

        // سالِ جاری پیش‌فرض است؛ سالِ دیگر شهریه‌ی خودش را می‌خواهد
        setTuition(f, null, 100).andExpect(status().isOk()).andExpect(jsonPath("$.academicYear").value(CURRENT));
        getAdmin(f, "/tuition-fee?academicYear=" + OLD).andExpect(status().isNotFound());
        backfill(f, OLD).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("tuition-fee-not-set")));

        setTuition(f, OLD, 80).andExpect(status().isOk()).andExpect(jsonPath("$.academicYear").value(OLD));
        getAdmin(f, "/tuition-fee").andExpect(jsonPath("$.amount").value(100)).andExpect(jsonPath("$.academicYear").value(CURRENT));
        getAdmin(f, "/tuition-fee?academicYear=" + OLD).andExpect(jsonPath("$.amount").value(80));

        // «صدورِ فاکتورهای سال» به مبلغ همان سال و مستقل برای هر سال
        backfill(f, null).andExpect(jsonPath("$.issuedCount").value(2));
        backfill(f, null).andExpect(jsonPath("$.issuedCount").value(0));
        backfill(f, OLD).andExpect(jsonPath("$.issuedCount").value(2));
        JsonNode oldInvoices = json(getAdmin(f, "/invoices?academicYear=" + OLD).andExpect(status().isOk()));
        assertThat(oldInvoices).hasSize(2);
        oldInvoices.forEach(invoice -> {
            assertThat(invoice.get("academicYear").asText()).isEqualTo(OLD);
            assertThat(invoice.get("amount").decimalValue()).isEqualByComparingTo("80");
        });
        assertThat(json(getAdmin(f, "/invoices").andExpect(status().isOk()))).hasSize(2);

        // سالِ ناشناخته: 400 و چیزی نوشته نمی‌شود
        setTuition(f, "1999-2000", 5).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-academic-year")));
        // ارقام فارسی همان سال را می‌دهد
        getAdmin(f, "/tuition-fee?academicYear=" + "۱۴۰۴-۱۴۰۵").andExpect(jsonPath("$.academicYear").value(OLD));
    }

    @Test
    void anEnrollmentIssuesTheCurrentYearsInvoiceWhenThatYearHasTuition() throws Exception {
        Fixture f = newSchool();
        setCurrentYear(f, CURRENT);
        setTuition(f, null, 120).andExpect(status().isOk());

        enroll(f, unique("fy.event"), "دانش‌آموز رویداد");

        // رویداد ثبت‌نام بعد از commit و ناهمگام اجرا می‌شود
        JsonNode invoices = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            invoices = json(getAdmin(f, "/invoices").andExpect(status().isOk()));
            if (invoices.size() == 1) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).get("academicYear").asText()).isEqualTo(CURRENT);
        assertThat(invoices.get(0).get("amount").decimalValue()).isEqualByComparingTo("120");
    }

    @Test
    void existingYearLessDataMustBeAttributedBeforeANewYearIsInvoicedAndConflictsAreRefused() throws Exception {
        Fixture f = newSchool();
        enroll(f, unique("fy.l1"), "قدیمی یک");
        enroll(f, unique("fy.l2"), "قدیمی دو");
        setTuition(f, null, 100).andExpect(status().isOk());
        backfill(f, null).andExpect(jsonPath("$.issuedCount").value(2));

        // مدیر سال جاری را انتخاب می‌کند و شهریه‌ی همان سال را می‌گذارد؛ هنوز فاکتورِ «بدون سال» هست
        setCurrentYear(f, CURRENT);
        setTuition(f, null, 150).andExpect(status().isOk());
        getAdmin(f, "/finance/unattributed").andExpect(jsonPath("$.tuitionFee").value(true))
                .andExpect(jsonPath("$.invoices").value(2));
        backfill(f, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("unattributed-invoices-exist")));

        // نسبت دادن به سالی که شهریه‌ی خودش را دارد با شهریه‌ی بدون سال تداخل دارد
        send(f, post("/api/admin/schools/" + f.schoolId() + "/finance/attribute-year"),
                "{\"academicYear\":\"" + CURRENT + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("attribution-conflict")));
        send(f, post("/api/admin/schools/" + f.schoolId() + "/finance/attribute-year"),
                "{\"academicYear\":\"1999-2000\"}").andExpect(status().isBadRequest());

        // به سالِ قبل نسبت داده می‌شود: شهریه و هر دو فاکتور، در یک اقدام
        send(f, post("/api/admin/schools/" + f.schoolId() + "/finance/attribute-year"),
                "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionAttributed").value(true)).andExpect(jsonPath("$.invoicesAttributed").value(2));
        getAdmin(f, "/finance/unattributed").andExpect(jsonPath("$.tuitionFee").value(false))
                .andExpect(jsonPath("$.invoices").value(0));
        getAdmin(f, "/tuition-fee?academicYear=" + OLD).andExpect(jsonPath("$.amount").value(100));
        assertThat(invoiceCount(f, OLD, OLD, true)).isEqualTo(2);

        // حالا سالِ جاری فاکتورهای خودش را می‌گیرد (به مبلغ ۱۵۰) و دوباره نسبت دادن چیزی تغییر نمی‌دهد
        backfill(f, null).andExpect(jsonPath("$.issuedCount").value(2));
        send(f, post("/api/admin/schools/" + f.schoolId() + "/finance/attribute-year"),
                "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionAttributed").value(false)).andExpect(jsonPath("$.invoicesAttributed").value(0));
    }

    @Test
    void familiesSeeOnlyTheCurrentYearButAnEarlierYearsInvoiceStaysPayable() throws Exception {
        Fixture f = newSchool();
        setCurrentYear(f, CURRENT);
        MockHttpSession student = enroll(f, unique("fy.fam"), "دانش‌آموز خانواده");
        setTuition(f, OLD, 80).andExpect(status().isOk());
        setTuition(f, null, 100).andExpect(status().isOk());
        backfill(f, OLD).andExpect(jsonPath("$.issuedCount").value(1));
        backfill(f, null).andExpect(jsonPath("$.issuedCount").value(1));

        // دانش‌آموز فقط فاکتورِ سالِ جاری را می‌بیند (سال‌های قبل پنهان است)
        mockMvc.perform(get("/api/me/schools/" + f.schoolId() + "/invoices").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(100));

        // ولی مدیر مالی فاکتورِ سالِ قبل را می‌بیند و پرداخت روی آن ثبت می‌شود (تصمیم Q3)
        JsonNode old = json(getAdmin(f, "/invoices?academicYear=" + OLD).andExpect(status().isOk()));
        long oldInvoiceId = 0;
        for (JsonNode invoice : old) {
            if (OLD.equals(invoice.get("academicYear").asText())) {
                oldInvoiceId = invoice.get("id").asLong();
            }
        }
        assertThat(oldInvoiceId).isPositive();
        send(f, post("/api/admin/schools/" + f.schoolId() + "/invoices/" + oldInvoiceId + "/payments"),
                "{\"amount\":30,\"method\":\"CASH\",\"note\":null}").andExpect(status().isOk());
        JsonNode after = json(getAdmin(f, "/invoices?academicYear=" + OLD).andExpect(status().isOk()));
        for (JsonNode invoice : after) {
            if (invoice.get("id").asLong() == oldInvoiceId) {
                assertThat(invoice.get("amountPaid").decimalValue()).isEqualByComparingTo("30");
            }
        }
    }
}
