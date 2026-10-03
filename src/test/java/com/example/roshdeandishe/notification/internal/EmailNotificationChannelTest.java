package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * تست واحدِ {@link EmailNotificationChannel}: تبدیل userId به نشانی ایمیل و
 * مسیر «ایمیلی پیدا نشد». بدون Spring context و بدون دیتابیس.
 */
class EmailNotificationChannelTest {

    private static final String FROM = "no-reply@roshdeandishe.test";

    private final IamFacade iamFacade = mock(IamFacade.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailNotificationChannel channel = new EmailNotificationChannel(iamFacade, mailSender, FROM);

    private static UserProfile profile(Long id, String email) {
        return new UserProfile(new UserId(id), email, "Test User", null);
    }

    @Test
    void resolvesUserIdToEmailAndSends() {
        when(iamFacade.findUser(new UserId(7L))).thenReturn(Optional.of(profile(7L, "parent@example.com")));

        boolean delivered = channel.sendToUser(7L, "hello");

        assertThat(delivered).isTrue();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("parent@example.com");
        assertThat(sent.getValue().getFrom()).isEqualTo(FROM);
        assertThat(sent.getValue().getText()).isEqualTo("hello");
    }

    @Test
    void returnsFalseAndSendsNothingWhenUserDoesNotExist() {
        when(iamFacade.findUser(new UserId(7L))).thenReturn(Optional.empty());

        assertThat(channel.sendToUser(7L, "hello")).isFalse();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void returnsFalseAndSendsNothingWhenUserHasBlankEmail() {
        when(iamFacade.findUser(new UserId(7L))).thenReturn(Optional.of(profile(7L, "  ")));

        assertThat(channel.sendToUser(7L, "hello")).isFalse();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void returnsFalseWhenSmtpFails() {
        when(iamFacade.findUser(new UserId(7L))).thenReturn(Optional.of(profile(7L, "parent@example.com")));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(channel.sendToUser(7L, "hello")).isFalse();
    }
}
