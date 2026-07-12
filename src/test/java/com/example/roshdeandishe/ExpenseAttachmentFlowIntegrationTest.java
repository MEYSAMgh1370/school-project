package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * پیوست‌های هزینه (اسکن فاکتور/رسید) - اولین آپلود فایل در این پروژه
 * (رجوع به doc طراحی ExpenseAttachmentContentEntity: هیچ زیرساخت
 * فایلی قبلاً وجود نداشت). آزمایش round-trip واقعی (نه mock)، سقف ۵
 * مگابایت دامنه‌ای، و اینکه لغو شدن هزینه آپلود بیشتر را می‌بندد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExpenseAttachmentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private long createDraftExpense(MockHttpSession session, String categoryName) throws Exception {
        MvcResult categoryResult = mockMvc.perform(post("/api/admin/schools/1/expense-categories")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + categoryName + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult expenseResult = mockMvc.perform(post("/api/admin/schools/1/expenses")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-05-10\",\"amount\":50000,\"categoryId\":" + categoryId +
                                ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(expenseResult.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void uploadListDownloadAndDeletePersianFilenameRoundTrip() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long expenseId = createDraftExpense(session, "پیوست-تست-رفت‌وبرگشت");

        byte[] fileBytes = "‌محتوای آزمایشی رسید".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "رسید.jpg", "image/jpeg", fileBytes);

        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/api/admin/schools/1/expenses/" + expenseId + "/attachments")
                        .file(file)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("رسید.jpg"))
                .andExpect(jsonPath("$.sizeBytes").value(fileBytes.length))
                .andReturn();
        long attachmentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        // متادیتا bytes ندارد
        mockMvc.perform(get("/api/admin/schools/1/expenses/" + expenseId + "/attachments")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("رسید.jpg"))
                .andExpect(jsonPath("$[0].content").doesNotExist());

        MvcResult downloadResult = mockMvc.perform(get("/api/admin/schools/1/expenses/" + expenseId +
                        "/attachments/" + attachmentId + "/content")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn();
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(fileBytes);

        mockMvc.perform(delete("/api/admin/schools/1/expenses/" + expenseId + "/attachments/" + attachmentId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/expenses/" + expenseId + "/attachments")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oversizeAttachmentIsRejectedWithDomainError() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long expenseId = createDraftExpense(session, "پیوست-تست-حجم");

        byte[] tooBig = new byte[6 * 1024 * 1024]; // بالاتر از سقف دامنه‌ای ۵ مگابایتی، زیر سقف servlet (۶ مگابایت)
        MockMultipartFile file = new MockMultipartFile("file", "بزرگ.bin", "application/octet-stream", tooBig);

        mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/api/admin/schools/1/expenses/" + expenseId + "/attachments")
                        .file(file)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-attachment")));
    }

    @Test
    void uploadToCancelledExpenseIsRejected() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long expenseId = createDraftExpense(session, "پیوست-تست-لغو");

        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/cancel")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile("file", "دیرشده.jpg", "image/jpeg", "data".getBytes());
        mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/api/admin/schools/1/expenses/" + expenseId + "/attachments")
                        .file(file)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("expense-not-editable")));
    }
}
