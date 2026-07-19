package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ماژول نمرات (academic.GradeController/MyGradesController) - حلِ CLAUDE.md
 * §11 Q8 با چهار تصمیم صریح: شِمای نمره‌دهی در سطح کلاس، ثبت فقط دستی (نه
 * مشتق از آزمون)، سطوح ثابتِ توصیفی + توضیح اختیاری، و نمره‌ی هر نوبت
 * (نه هر ارزشیابی). الگوی لاگین از ClassManagementFlowIntegrationTest کپی
 * شده است.
 * <p>
 * تصمیمِ امنیتیِ کلیدی که این تست تأیید می‌کند: GradeController عمداً با
 * GRADES_EDIT گیت می‌شود، نه GRADES_VIEW (که STUDENT/PARENT هم دارند) - وگرنه
 * یک دانش‌آموز می‌توانست با فراخوانی مستقیمِ endpoint ادمینی، نمره‌ی کل
 * هم‌کلاسی‌هایش را ببیند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GradeManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.roshdeandishe.iam.IamFacade iamFacade;

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

    @Test
    void adminCanRecordAndListNumericGrade() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه هفتم - نمره‌ی عددی", "NUMERIC");
        long studentId = enrollStudent(admin, "numeric.grade.student@roshdeandishe.test", "دانش‌آموز نمره‌ی عددی", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":18.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheme").value("NUMERIC"))
                .andExpect(jsonPath("$.numericScore").value(18.50));

        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + " && @.numericScore == 18.50)]").exists());
    }

    @Test
    void adminCanRecordDescriptiveGradeWithComment() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه اول - نمره‌ی توصیفی", "DESCRIPTIVE");
        long studentId = enrollStudent(admin, "descriptive.grade.student@roshdeandishe.test", "دانش‌آموز نمره‌ی توصیفی", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"descriptiveLevel\":\"GOOD\",\"comment\":\"تلاش خوبی داشت\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheme").value("DESCRIPTIVE"))
                .andExpect(jsonPath("$.descriptiveLevel").value("GOOD"))
                .andExpect(jsonPath("$.comment").value("تلاش خوبی داشت"));
    }

    @Test
    void numericScoreOnADescriptiveClassIsSchemeMismatch() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه دوم - عدم تطابق", "DESCRIPTIVE");
        long studentId = enrollStudent(admin, "mismatch.grade.student@roshdeandishe.test", "دانش‌آموز عدم تطابق", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":15}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("grading-scheme-mismatch")));
    }

    @Test
    void numericScoreOutOfRangeIsRejected() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه سوم - بازه‌ی نمره", "NUMERIC");
        long studentId = enrollStudent(admin, "outofrange.grade.student@roshdeandishe.test", "دانش‌آموز بازه‌ی نمره", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-grade")));
    }

    @Test
    void reRecordingSameStudentClassTermUpdatesInPlaceInsteadOfDuplicating() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه چهارم - ثبتِ دوباره", "NUMERIC");
        long studentId = enrollStudent(admin, "reentry.grade.student@roshdeandishe.test", "دانش‌آموز ثبتِ دوباره", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":10}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":15}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].numericScore").value(15.0));
    }

    @Test
    void gradeForStudentNotInThatClassIsRejected() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classA = createClass(admin, "پایه پنجم - آ", "NUMERIC");
        long classB = createClass(admin, "پایه پنجم - ب", "NUMERIC");
        long studentId = enrollStudent(admin, "wrongclass.grade.student@roshdeandishe.test", "دانش‌آموز کلاس اشتباه", classA);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classB
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":15}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-in-class")));
    }

    @Test
    void teacherCanRecordAndListGrades() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه ششم - معلم", "NUMERIC");
        long studentId = enrollStudent(admin, "teacher.grade.student@roshdeandishe.test", "دانش‌آموز معلم", classId);

        MockHttpSession teacher = registerAndLogin("grade.teacher@roshdeandishe.test", "معلمِ نمره‌دهنده");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"grade.teacher@roshdeandishe.test\",\"fullName\":\"معلمِ نمره‌دهنده\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":12}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(teacher)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());
    }

    @Test
    void studentCannotAccessAdminGradeEndpointsEvenThoughTheyHoldGradesView() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه هفتم - محدودیت دانش‌آموز", "NUMERIC");
        String studentEmail = "restricted.grade.student@roshdeandishe.test";
        // اول register (با رمز) بعد enroll همان ایمیل تا بشود با آن لاگین کرد -
        // findOrCreateUser رمز موجود را دست نمی‌زند (الگوی FinancePaymentFlowIntegrationTest)
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز محدود");
        long studentId = enrollStudent(admin, studentEmail, "دانش‌آموز محدود", classId);

        // STUDENT فقط GRADES_VIEW دارد، نه GRADES_EDIT - این کنترلر عمداً فقط
        // با GRADES_EDIT گیت شده تا نشتِ نمره‌ی هم‌کلاسی‌ها ممکن نباشد
        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(studentSession)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(studentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void freshSchoolGetsFreeAcademicTierAndRevocationYieldsModuleNotEntitled() throws Exception {
        MockHttpSession founder = registerAndLogin("grade.founder@roshdeandishe.test", "بنیان‌گذار بسته‌ی رایگان آموزشی");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بسته‌ی رایگان آموزشی\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // بسته‌ی رایگان ACADEMIC را شامل می‌شود (CLAUDE.md §11 Q10) - فهرست
        // کلاس‌ها باید 200 (خالی) برگردد، نه module-not-entitled
        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/classes")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founder))
                .andExpect(status().isOk());

        // پس‌گرفتن ACADEMIC از طریق facade (تنها مسیر مجاز - §10)
        iamFacade.setSchoolEntitlement(schoolId, new com.example.roshdeandishe.iam.IamFacade.ModuleCode("ACADEMIC"), false);

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/grades")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founder)
                        .param("classId", "1")
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }

    @Test
    void myGradesReturnsOwnAndWardsGradesOnly() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه هشتم - نمره‌های من", "NUMERIC");

        String studentEmail = "mygrades.student@roshdeandishe.test";
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز نمره‌های من");
        long studentId = enrollStudent(admin, studentEmail, "دانش‌آموز نمره‌های من", classId);

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId
                                + ",\"academicYear\":\"1404-1405\",\"term\":\"FIRST\",\"numericScore\":17}"))
                .andExpect(status().isOk());

        // دانش‌آموز خودش نمره‌ی خودش را می‌بیند - بدون هیچ Permission خاصی
        mockMvc.perform(get("/api/me/schools/1/grades").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + " && @.numericScore == 17)]").exists());

        // ولی: register + عضویت PARENT + لینک guardian توسط ادمین
        String parentEmail = "mygrades.parent@roshdeandishe.test";
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولیِ نمره‌های من");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولیِ نمره‌های من\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString()).get("userId").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        // ولیِ لینک‌شده هم نمره‌ی همین فرزند را می‌بیند
        mockMvc.perform(get("/api/me/schools/1/grades").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + " && @.numericScore == 17)]").exists());

        // کاربر بی‌ربط: لیست خالی، نه 403 - endpoint فقط داده‌ی خودِ کاربر را برمی‌گرداند
        MockHttpSession strangerSession = registerAndLogin("mygrades.stranger@roshdeandishe.test", "کاربر بی‌ربط");
        mockMvc.perform(get("/api/me/schools/1/grades").session(strangerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
