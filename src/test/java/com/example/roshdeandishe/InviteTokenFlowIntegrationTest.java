package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InviteTokenFlowIntegrationTest {

    @Autowired
    private IamFacade iamFacade;

    @Test
    void issueAndRedeemInviteTokenActivatesAccount() {
        IamFacade.UserId userId = iamFacade.findOrCreateUser(
                "invite-flow-test@example.com", "Invite Flow Test"
        );
        Long membershipId = iamFacade.createMembership(userId, 1L, "STUDENT");

        Optional<String> firstIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(firstIssue).isPresent();
        String token = firstIssue.get();

        // idempotent reuse: issuing again before redemption returns the same token
        Optional<String> secondIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(secondIssue).contains(token);

        iamFacade.redeemInviteToken(token, "brandNewPassw0rd");

        // redeeming again must fail (already redeemed)
        assertThatThrownBy(() -> iamFacade.redeemInviteToken(token, "anotherPassword1"))
                .isInstanceOf(IamException.class);

        // issuing again after activation is a no-op (already has a password)
        Optional<String> thirdIssue = iamFacade.issueInviteToken(membershipId);
        assertThat(thirdIssue).isEmpty();
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
        String token = iamFacade.issueInviteToken(membershipId).orElseThrow();

        assertThatThrownBy(() -> iamFacade.redeemInviteToken(token, "short"))
                .isInstanceOf(IamException.class);
    }
}
