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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * صفحه‌ی «فاکتورهای من» (/my-invoices) - برای STUDENT/PARENT، از UX_ROADMAP.md
 * (پرسونای PARENT/STUDENT): «یک /my-invoices-style page طبیعی است، هنوز
 * ساخته نشده». یک shell نازک مثل بقیه‌ی صفحه‌های مدیریتی؛ داده کاملاً
 * client-side از finance.MyInvoicesController خوانده می‌شود که پیش از این با
 * FinancePaymentFlowIntegrationTest.myInvoicesReturnsOwnAndWardsInvoicesOnly
 * پوشش کامل REST دارد - این فایل فقط پوسته‌ی صفحه را تست می‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyInvoicesPageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
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

    @Test
    void unauthenticatedMyInvoicesPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/my-invoices"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void myInvoicesPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("myinvoices.viewer@roshdeandishe.test", "بیننده فاکتور");

        mockMvc.perform(get("/my-invoices").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("فاکتورهای من")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"/js/money.js\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money.formatAmount(invoice.amount)")))
                // مانده بر پایه‌ی قابل‌پرداخت (مبلغ - تخفیف) و بخش اقساط
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money.formatAmount(remaining(invoice))")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money.formatAmount(payable(invoice))")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("تخفیف")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("اقساط")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("قسط بعدی")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(":aria-expanded")))
                // تخفیف مشروط
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hasPendingDiscount(invoice)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("تخفیف مشروط")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invoice.pendingDiscountDeadline")))
                // لینک‌های ناوبری روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }
}
