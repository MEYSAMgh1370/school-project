package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.notification.SchoolBotFacade.SchoolBotInfo;
import com.example.roshdeandishe.notification.SchoolBotFacade.SchoolBotState;
import com.example.roshdeandishe.notification.shared.NotificationError;
import com.example.roshdeandishe.notification.shared.NotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchoolBotServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final long SCHOOL = 5L;

    private final BaleClient baleClient = mock(BaleClient.class);
    private final SchoolBotRepository bots = mock(SchoolBotRepository.class);
    private final BaleLinkRepository links = mock(BaleLinkRepository.class);
    private final IamFacade iam = mock(IamFacade.class);
    private final TokenCipher cipher = new TokenCipher(randomKey());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SchoolBotService service;

    private static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private SchoolBotService build(TokenCipher c) {
        return new SchoolBotService(bots, links, baleClient, c, iam, new RegistrationAttempts(clock),
                TransactionOperations.withoutTransaction(), "https://app.example/", clock);
    }

    @BeforeEach
    void setUp() {
        service = build(cipher);
        when(iam.currentUserId()).thenReturn(new UserId(42L));
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.empty());
        when(bots.findByBotUserId(anyLong())).thenReturn(Optional.empty());
        when(bots.saveAndFlush(any(SchoolBotEntity.class))).thenAnswer(inv -> {
            SchoolBotEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(77L);
            }
            when(bots.findById(e.getId())).thenReturn(Optional.of(e));
            return e;
        });
        when(baleClient.getMe(anyString()))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(1L, "school_bot")));
        when(baleClient.setWebhook(anyString(), anyString())).thenReturn(true);
    }

    private SchoolBotEntity saved() {
        ArgumentCaptor<SchoolBotEntity> captor = ArgumentCaptor.forClass(SchoolBotEntity.class);
        verify(bots).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private String webhookUrl(String token) {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(baleClient).setWebhook(eq(token), url.capture());
        return url.getValue();
    }

    private static NotificationError errorOf(Runnable action) {
        try {
            action.run();
        } catch (NotificationException e) {
            return e.error();
        }
        throw new AssertionError("expected NotificationException");
    }

    private SchoolBotEntity existing(long id, long botUserId, String hash) {
        SchoolBotEntity e = new SchoolBotEntity();
        e.setId(id);
        e.setSchoolId(SCHOOL);
        e.setBotUserId(botUserId);
        e.setBotUsername("old_bot");
        e.setWebhookSecretHash(hash);
        e.setStatus(SchoolBotStatus.DISABLED);
        e.setLastError("x");
        e.setLastErrorAt(NOW);
        TokenCipher.Encrypted enc = cipher.encrypt("old:token");
        e.setTokenCiphertext(enc.ciphertext());
        e.setTokenKeyVersion(enc.keyVersion());
        e.setCreatedAt(NOW.minusSeconds(100));
        e.setCreatedBy(7L);
        e.setUpdatedAt(NOW.minusSeconds(100));
        return e;
    }

    @Test
    void registersAndStoresOnlyEncryptedTokenAndSecretHash() {
        SchoolBotInfo info = service.register(SCHOOL, "123:abc");

        SchoolBotEntity e = saved();
        String url = webhookUrl("123:abc");
        String secret = url.substring(url.lastIndexOf('/') + 1);
        assertThat(e.getTokenCiphertext()).isNotEqualTo("123:abc");
        assertThat(cipher.decrypt(e.getTokenCiphertext(), e.getTokenKeyVersion())).isEqualTo("123:abc");
        assertThat(e.getWebhookSecretHash()).isEqualTo(WebhookSecrets.hash(secret));
        assertThat(e.getStatus()).isEqualTo(SchoolBotStatus.ACTIVE);
        assertThat(e.getSchoolId()).isEqualTo(SCHOOL);
        assertThat(e.getBotUserId()).isEqualTo(1L);
        assertThat(e.getBotUsername()).isEqualTo("school_bot");
        assertThat(e.getCreatedBy()).isEqualTo(42L);
        assertThat(e.getUpdatedBy()).isEqualTo(42L);
        assertThat(info.username()).isEqualTo("school_bot");
        assertThat(info.status()).isEqualTo(SchoolBotState.ACTIVE);
    }

    @Test
    void webhookUrlUsesBaseUrlAndSecret() {
        service.register(SCHOOL, "123:abc");

        assertThat(webhookUrl("123:abc")).startsWith("https://app.example/api/bale/webhook/");
    }

    @Test
    void tokenIsTrimmedAndBlankIsInvalidWithoutCallingBale() {
        service.register(SCHOOL, "  123:abc \n");
        verify(baleClient).getMe("123:abc");

        BaleClient fresh = mock(BaleClient.class);
        SchoolBotService other = new SchoolBotService(bots, links, fresh, cipher, iam,
                new RegistrationAttempts(clock), TransactionOperations.withoutTransaction(), "https://app.example", clock);
        assertThat(errorOf(() -> other.register(SCHOOL, "   "))).isInstanceOf(NotificationError.BotTokenInvalid.class);
        assertThat(errorOf(() -> other.register(SCHOOL, null))).isInstanceOf(NotificationError.BotTokenInvalid.class);
        verifyNoInteractions(fresh);
    }

    @Test
    void invalidTokenIsRefusedAndNothingSaved() {
        when(baleClient.getMe("bad")).thenReturn(new BaleClient.BotInfoResult.InvalidToken());

        assertThat(errorOf(() -> service.register(SCHOOL, "bad"))).isInstanceOf(NotificationError.BotTokenInvalid.class);
        verify(bots, never()).saveAndFlush(any());
    }

    @Test
    void unreachableBaleIsRefusedAndNothingSaved() {
        when(baleClient.getMe("t")).thenReturn(new BaleClient.BotInfoResult.Unreachable());

        assertThat(errorOf(() -> service.register(SCHOOL, "t"))).isInstanceOf(NotificationError.BaleUnavailable.class);
        verify(bots, never()).saveAndFlush(any());
    }

    @Test
    void webhookFailureForNewBotDeletesTheReservedRow() {
        when(baleClient.setWebhook(anyString(), anyString())).thenReturn(false);

        assertThat(errorOf(() -> service.register(SCHOOL, "t"))).isInstanceOf(NotificationError.BaleUnavailable.class);
        verify(bots).deleteById(77L);
        verify(links, never()).deleteByBotId(anyLong());
    }

    @Test
    void webhookFailureForReplacementRestoresThePreviousRow() {
        SchoolBotEntity old = existing(3L, 1L, "oldhash");
        String oldCipher = old.getTokenCiphertext();
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(bots.findById(3L)).thenReturn(Optional.of(old));
        when(baleClient.getMe("new:tok"))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(2L, "new_bot")));
        when(baleClient.setWebhook(anyString(), anyString())).thenReturn(false);

        assertThat(errorOf(() -> service.register(SCHOOL, "new:tok"))).isInstanceOf(NotificationError.BaleUnavailable.class);

        assertThat(old.getBotUserId()).isEqualTo(1L);
        assertThat(old.getBotUsername()).isEqualTo("old_bot");
        assertThat(old.getTokenCiphertext()).isEqualTo(oldCipher);
        assertThat(old.getWebhookSecretHash()).isEqualTo("oldhash");
        assertThat(old.getStatus()).isEqualTo(SchoolBotStatus.DISABLED);
        assertThat(old.getLastError()).isEqualTo("x");
        assertThat(old.getLastErrorAt()).isEqualTo(NOW);
        assertThat(old.getUpdatedBy()).isNull();
        verify(links, never()).deleteByBotId(anyLong());
    }

    @Test
    void concurrentSaveConflictIsBotAlreadyRegistered() {
        when(bots.saveAndFlush(any(SchoolBotEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(errorOf(() -> service.register(SCHOOL, "t"))).isInstanceOf(NotificationError.BotAlreadyRegistered.class);
        verify(baleClient, never()).setWebhook(anyString(), anyString());
    }

    @Test
    void linksAreDeletedOnlyAfterWebhookSucceeds() {
        SchoolBotEntity old = existing(3L, 1L, "oldhash");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(baleClient.getMe("new:tok"))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(2L, "new_bot")));

        service.register(SCHOOL, "new:tok");

        InOrder order = inOrder(bots, baleClient, links);
        order.verify(bots).saveAndFlush(any());
        order.verify(baleClient).setWebhook(anyString(), anyString());
        order.verify(links).deleteByBotId(3L);
    }

    @Test
    void botOfAnotherSchoolIsRefused() {
        SchoolBotEntity other = existing(9L, 1L, "h");
        other.setSchoolId(99L);
        when(bots.findByBotUserId(1L)).thenReturn(Optional.of(other));

        assertThat(errorOf(() -> service.register(SCHOOL, "t"))).isInstanceOf(NotificationError.BotAlreadyRegistered.class);
        verify(baleClient, never()).setWebhook(anyString(), anyString());
        verify(bots, never()).saveAndFlush(any());
    }

    @Test
    void missingEncryptionKeyIsRefusedBeforeCallingBale() {
        SchoolBotService noKey = build(new TokenCipher(""));

        assertThat(errorOf(() -> noKey.register(SCHOOL, "t"))).isInstanceOf(NotificationError.BotEncryptionUnavailable.class);
        verifyNoInteractions(baleClient);
    }

    @Test
    void eleventhAttemptInAnHourIsRefused() {
        when(baleClient.getMe("bad")).thenReturn(new BaleClient.BotInfoResult.InvalidToken());
        for (int i = 0; i < 10; i++) {
            assertThat(errorOf(() -> service.register(SCHOOL, "bad"))).isInstanceOf(NotificationError.BotTokenInvalid.class);
        }

        assertThat(errorOf(() -> service.register(SCHOOL, "bad"))).isInstanceOf(NotificationError.TooManyAttempts.class);
    }

    @Test
    void replacingWithDifferentBotDeletesOldLinksAndSecret() {
        SchoolBotEntity old = existing(3L, 1L, "oldhash");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(baleClient.getMe("new:tok"))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(2L, "new_bot")));

        service.register(SCHOOL, "new:tok");

        verify(links).deleteByBotId(3L);
        // وب‌هوکِ رباتِ قبلی با توکنِ قبلی برداشته می‌شود
        verify(baleClient).deleteWebhook("old:token");
        verify(baleClient, never()).deleteWebhook("new:tok");
        SchoolBotEntity e = saved();
        assertThat(e.getId()).isEqualTo(3L);
        assertThat(e.getBotUserId()).isEqualTo(2L);
        assertThat(e.getWebhookSecretHash()).isNotEqualTo("oldhash");
        assertThat(e.getStatus()).isEqualTo(SchoolBotStatus.ACTIVE);
        assertThat(e.getLastError()).isNull();
        assertThat(e.getLastErrorAt()).isNull();
        assertThat(e.getCreatedBy()).isEqualTo(7L);
        assertThat(e.getUpdatedBy()).isEqualTo(42L);
    }

    @Test
    void replacingWithSameBotKeepsLinks() {
        SchoolBotEntity old = existing(3L, 1L, "oldhash");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(bots.findByBotUserId(1L)).thenReturn(Optional.of(old));

        service.register(SCHOOL, "123:abc");

        verify(links, never()).deleteByBotId(anyLong());
        verify(baleClient, never()).deleteWebhook(anyString());
        assertThat(saved().getWebhookSecretHash()).isNotEqualTo("oldhash");
    }

    @Test
    void newRegistrationNeverCallsDeleteWebhook() {
        service.register(SCHOOL, "123:abc");

        verify(baleClient, never()).deleteWebhook(anyString());
    }

    @Test
    void failedSetWebhookOnDifferentBotDoesNotRemoveTheOldWebhook() {
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(existing(3L, 1L, "oldhash")));
        when(baleClient.getMe("new:tok"))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(2L, "new_bot")));
        when(baleClient.setWebhook(eq("new:tok"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.register(SCHOOL, "new:tok")).isInstanceOf(NotificationException.class);

        verify(baleClient, never()).deleteWebhook(anyString());
    }

    @Test
    void oldWebhookRemovalFailureIsIgnoredOnDifferentBotReplace() {
        SchoolBotEntity old = existing(3L, 1L, "oldhash");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(baleClient.getMe("new:tok"))
                .thenReturn(new BaleClient.BotInfoResult.Found(new BaleClient.BotInfo(2L, "new_bot")));
        when(baleClient.deleteWebhook("old:token")).thenThrow(new IllegalStateException("boom"));

        SchoolBotInfo info = service.register(SCHOOL, "new:tok");

        assertThat(info.username()).isEqualTo("new_bot");
        verify(baleClient).deleteWebhook("old:token");
    }

    @Test
    void deleteCallsDeleteWebhookAndRemovesRow() {
        SchoolBotEntity old = existing(3L, 1L, "h");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(baleClient.deleteWebhook("old:token")).thenReturn(false);

        service.delete(SCHOOL);

        InOrder order = inOrder(bots, baleClient);
        order.verify(bots).delete(old);
        order.verify(baleClient).deleteWebhook("old:token");
    }

    @Test
    void deleteWithoutBotIsNotFound() {
        assertThat(errorOf(() -> service.delete(SCHOOL))).isInstanceOf(NotificationError.SchoolBotNotFound.class);
    }

    @Test
    void findReportsLinkedMembers() {
        SchoolBotEntity old = existing(3L, 1L, "h");
        when(bots.findBySchoolId(SCHOOL)).thenReturn(Optional.of(old));
        when(links.countByBotId(3L)).thenReturn(4L);

        SchoolBotInfo info = service.find(SCHOOL).orElseThrow();

        assertThat(info.linkedMembers()).isEqualTo(4L);
        assertThat(info.status()).isEqualTo(SchoolBotState.DISABLED);
        assertThat(info.lastError()).isEqualTo("x");
    }

    @Test
    void markDisabledSetsStatusAndError() {
        service.markDisabled(3L, "boom");

        verify(bots).markStatus(3L, SchoolBotStatus.DISABLED, "boom", NOW);
        verify(bots, never()).saveAndFlush(any());
    }
}
