package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.InviteIssued;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InviteTokenFlowIntegrationTest {

    // SMTP درون-تستی؛ قبل از هر تست بالا می‌آید و بعد از آن خاموش می‌شود
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired
    private IamFacade iamFacade;

    @Test
    void issueAndRedeemInviteTokenActivatesAccount() {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(
                "invite-flow-test@example.com", "Invite Flow Test"
        );
        Long membershipId = iamFacade.createMembership(userId, 1L, "STUDENT");

        Optional<InviteIssued> firstIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(firstIssue).isPresent();
        String token = firstIssue.get().token();

        // idempotent reuse: issuing again before redemption returns the same token
        Optional<InviteIssued> secondIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(secondIssue.orElseThrow().token()).isEqualTo(token);

        iamFacade.redeemInviteToken(token, "brandNewPassw0rd");

        // redeeming again must fail (already redeemed)
        assertThatThrownBy(() -> iamFacade.redeemInviteToken(token, "anotherPassword1"))
                .isInstanceOf(IamException.class);

        // issuing again after activation is a no-op (already has a password)
        Optional<InviteIssued> thirdIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(thirdIssue).isEmpty();
    }

    @Test
    void issuingInviteTokenDeliversActivationEmail() throws Exception {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(
                "invite-email-test@example.com", "Invite Email Test"
        );
        Long membershipId = iamFacade.createMembership(userId, 1L, "STUDENT");

        InviteIssued issued = iamFacade.issueInviteToken(membershipId).orElseThrow();
        assertThat(issued.emailSent()).isTrue();

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage message = received[0];
        assertThat(GreenMailUtil.getAddressList(message.getAllRecipients()))
                .contains("invite-email-test@example.com");
        // getContent() متن را با توجه به Content-Transfer-Encoding رمزگشایی
        // می‌کند (متن فارسی base64 می‌شود؛ GreenMailUtil.getBody خام برمی‌گرداند)
        assertThat(message.getContent().toString()).contains("/activate?token=" + issued.token());
    }

    @Test
    void issuingInviteTokenSurvivesSmtpOutage() {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(
                "invite-smtp-down-test@example.com", "SMTP Down Test"
        );
        Long membershipId = iamFacade.createMembership(userId, 1L, "STUDENT");

        // شبیه‌سازی قطع بودن SMTP: صدور توکن نباید شکست بخورد، فقط
        // emailSent=false برمی‌گردد (ادمین لینک را دستی تحویل می‌دهد)
        greenMail.stop();

        InviteIssued issued = iamFacade.issueInviteToken(membershipId).orElseThrow();
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.emailSent()).isFalse();
    }

    @Test
    void redeemingUnknownTokenThrows() {
        assertThatThrownBy(() -> iamFacade.redeemInviteToken("does-not-exist", "somePassword1"))
                .isInstanceOf(IamException.class);
    }

    @Test
    void redeemingWithTooShortPasswordThrows() {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(
                "invite-flow-short-pw@example.com", "Short PW Test"
        );
        Long membershipId = iamFacade.createMembership(userId, 1L, "STUDENT");
        String token = iamFacade.issueInviteToken(membershipId).orElseThrow().token();

        assertThatThrownBy(() -> iamFacade.redeemInviteToken(token, "short"))
                .isInstanceOf(IamException.class);
    }
}
