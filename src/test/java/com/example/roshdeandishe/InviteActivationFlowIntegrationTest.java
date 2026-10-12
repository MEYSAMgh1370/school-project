package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.InviteIssued;
import com.example.roshdeandishe.iam.IamFacade.InviteState;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * مسیرِ دعوتِ یک عضو تا اولین ورود: متنِ ایمیل و صفحه‌ی فعال‌سازی می‌گویند به کدام مدرسه و با چه نقشی دعوت
 * شده‌ایم، وضعیتِ توکنِ نامعتبر روشن است، و پاسخِ فعال‌سازی ایمیلِ حساب را برمی‌گرداند تا کاربر خودکار وارد شود.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InviteActivationFlowIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theInviteEmailAndTheActivationPageNameTheSchoolAndTheRole() throws Exception {
        String schoolName = schoolFacade.findSchool(new SchoolId(1L)).orElseThrow().name();
        String email = "invited-teacher-" + System.nanoTime() + "@example.test";
        InviteIssued issued = invite(email, "TEACHER");

        MimeMessage message = greenMail.getReceivedMessages()[greenMail.getReceivedMessages().length - 1];
        String body = message.getContent().toString();
        assertThat(body).contains("شما به‌عنوان معلم در " + schoolName + " دعوت شده‌اید.");

        mockMvc.perform(get("/activate").param("token", issued.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(email)))
                .andExpect(content().string(containsString("دعوت‌شده به‌عنوان")))
                .andExpect(content().string(containsString("<strong>معلم</strong>")))
                .andExpect(content().string(containsString(schoolName)))
                // با لینک، فیلدِ «کد دعوت» دستی نیست
                .andExpect(content().string(containsString("data-manual-token=\"false\"")));
    }

    @Test
    void openingTheActivationPageWithoutALinkKeepsTheManualCodeField() throws Exception {
        mockMvc.perform(get("/activate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-manual-token=\"true\"")))
                .andExpect(content().string(containsString("فعال‌سازی حساب")))
                .andExpect(content().string(not(containsString("دعوت‌شده به‌عنوان"))));
    }

    @Test
    void anUnknownExpiredAndUsedLinkEachExplainThemselvesAndOfferNoForm() throws Exception {
        String email = "invited-states-" + System.nanoTime() + "@example.test";
        InviteIssued issued = invite(email, "STUDENT");

        assertThat(iamFacade.describeInvite("no-such-token").state()).isEqualTo(InviteState.UNKNOWN);
        mockMvc.perform(get("/activate").param("token", "no-such-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("این لینک دعوت نامعتبر است")))
                .andExpect(content().string(not(containsString("تکرار رمز عبور"))))
                .andExpect(content().string(not(containsString(email))));

        jdbcTemplate.update("update iam.invite_tokens set expires_at = now() - interval '1 minute' where token = ?",
                issued.token());
        assertThat(iamFacade.describeInvite(issued.token()).state()).isEqualTo(InviteState.EXPIRED);
        mockMvc.perform(get("/activate").param("token", issued.token()))
                .andExpect(content().string(containsString("منقضی شده است")))
                .andExpect(content().string(containsString("دعوت‌نامه‌ی تازه‌ای")))
                .andExpect(content().string(not(containsString(email))));

        jdbcTemplate.update("update iam.invite_tokens set expires_at = now() + interval '1 day', redeemed_at = now() where token = ?",
                issued.token());
        assertThat(iamFacade.describeInvite(issued.token()).state()).isEqualTo(InviteState.USED);
        mockMvc.perform(get("/activate").param("token", issued.token()))
                .andExpect(content().string(containsString("قبلاً استفاده شده است")))
                .andExpect(content().string(not(containsString("تکرار رمز عبور"))));
    }

    @Test
    void activationReturnsTheAccountEmailSoThePageCanSignTheUserIn() throws Exception {
        String email = "invited-login-" + System.nanoTime() + "@example.test";
        InviteIssued issued = invite(email, "TEACHER");

        mockMvc.perform(post("/api/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + issued.token() + "\",\"newPassword\":\"TeacherPass-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"TeacherPass-2026\"}"))
                .andExpect(status().isOk());
    }

    private InviteIssued invite(String email, String roleCode) {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(email, "عضو دعوت‌شده");
        Long membershipId = iamFacade.createMembership(userId, 1L, roleCode);
        return iamFacade.issueInviteToken(1L, membershipId).orElseThrow();
    }
}
