package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * کانال بله را بدون Spring و Docker می‌آزماید: بدونِ لینک false (و بدون هیچ
 * تماسی با بله)، با لینک پیام به chatId لینک‌شده می‌رود و نتیجه‌ی کلاینت
 * برگردانده می‌شود.
 */
class BaleChannelTest {

    private static class RecordingClient extends BaleClient {
        private final DeliveryResult result;
        private final int httpStatus;
        Long chatId;
        String text;
        String token;
        int calls;

        RecordingClient(DeliveryResult result) {
            this(result, 0);
        }

        RecordingClient(DeliveryResult result, int httpStatus) {
            super("http://localhost:0");
            this.result = result;
            this.httpStatus = httpStatus;
        }

        @Override
        SendResult deliverDetailed(String token, Long chatId, String text) {
            this.token = token;
            this.chatId = chatId;
            this.text = text;
            calls++;
            return new SendResult(result, httpStatus);
        }
    }

    private static final long SCHOOL = 5L;
    private static final long BOT = 3L;
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    private final BaleLinkRepository links = mock(BaleLinkRepository.class);
    private final SchoolBotRepository bots = mock(SchoolBotRepository.class);
    private final TokenCipher cipher = mock(TokenCipher.class);

    private BaleChannel channel(BaleClient client) {
        return new BaleChannel(links, client, new BotTokens("shared-token", bots, cipher), bots,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SchoolBotEntity activeBot() {
        SchoolBotEntity bot = new SchoolBotEntity();
        bot.setId(BOT);
        bot.setSchoolId(SCHOOL);
        bot.setStatus(SchoolBotStatus.ACTIVE);
        bot.setTokenCiphertext("ct");
        bot.setTokenKeyVersion(1);
        when(bots.findBySchoolIdAndStatus(SCHOOL, SchoolBotStatus.ACTIVE)).thenReturn(Optional.of(bot));
        when(bots.findById(BOT)).thenReturn(Optional.of(bot));
        when(cipher.decrypt("ct", 1)).thenReturn("school-token");
        return bot;
    }

    @Test
    void unlinkedUserIsUnreachableAndNothingIsSent() {
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        RecordingClient client = new RecordingClient(DeliveryResult.SENT);

        assertThat(channel(client).send(7L, "سلام")).isFalse();
        assertThat(client.chatId).isNull();
    }

    @Test
    void linkedUserGetsTheMessageOnTheirLinkedChat() {
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT);

        assertThat(channel(client).send(7L, "سلام")).isTrue();
        assertThat(client.chatId).isEqualTo(555L);
        assertThat(client.text).isEqualTo("سلام");
    }

    @Test
    void aFailedSendIsReportedAsNotDelivered() {
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));

        assertThat(channel(new RecordingClient(DeliveryResult.TEMPORARY_FAILURE)).send(7L, "سلام")).isFalse();
    }

    @Test
    void theDetailedResultDistinguishesNotLinkedFromAClientFailure() {
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        assertThat(channel(new RecordingClient(DeliveryResult.SENT)).deliver(7L, "سلام"))
                .isEqualTo(DeliveryResult.NOT_LINKED);

        when(links.findByUser(BotRef.SHARED, 8L)).thenReturn(Optional.of(new BaleLinkEntity(null, 8L, 556L, Instant.now())));
        assertThat(channel(new RecordingClient(DeliveryResult.TEMPORARY_FAILURE)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.TEMPORARY_FAILURE);
        assertThat(channel(new RecordingClient(DeliveryResult.UNDELIVERABLE)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(channel(new RecordingClient(DeliveryResult.SENT)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.SENT);
    }

    @Test
    void schoolWithActiveBotUsesItsLinkAndToken() {
        activeBot();
        when(links.findByUser(BotRef.school(BOT, SCHOOL), 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(BOT, 7L, 777L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT, 200);

        assertThat(channel(client).deliver(7L, SCHOOL, "سلام")).isEqualTo(DeliveryResult.SENT);
        assertThat(client.chatId).isEqualTo(777L);
        assertThat(client.token).isEqualTo("school-token");
    }

    @Test
    void userNotLinkedToSchoolBotIsNotLinkedEvenWithSharedLink() {
        activeBot();
        when(links.findByUser(BotRef.school(BOT, SCHOOL), 7L)).thenReturn(Optional.empty());
        when(links.findByUser(BotRef.SHARED, 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT, 200);

        assertThat(channel(client).deliver(7L, SCHOOL, "سلام")).isEqualTo(DeliveryResult.NOT_LINKED);
        assertThat(client.calls).isZero();
    }

    @Test
    void schoolWithoutBotUsesSharedLink() {
        when(bots.findBySchoolIdAndStatus(SCHOOL, SchoolBotStatus.ACTIVE)).thenReturn(Optional.empty());
        when(links.findByUser(BotRef.SHARED, 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT, 200);

        assertThat(channel(client).deliver(7L, SCHOOL, "سلام")).isEqualTo(DeliveryResult.SENT);
        assertThat(client.chatId).isEqualTo(555L);
        assertThat(client.token).isEqualTo("shared-token");
    }

    @Test
    void disabledSchoolBotRoutesToSharedBot() {
        // فقط ACTIVE جست‌وجو می‌شود؛ ربات DISABLED پیدا نمی‌شود
        SchoolBotEntity disabled = new SchoolBotEntity();
        disabled.setId(BOT);
        disabled.setSchoolId(SCHOOL);
        disabled.setStatus(SchoolBotStatus.DISABLED);
        when(bots.findBySchoolIdAndStatus(SCHOOL, SchoolBotStatus.ACTIVE)).thenReturn(Optional.empty());
        when(links.findByUser(BotRef.SHARED, 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT, 200);

        assertThat(channel(client).deliver(7L, SCHOOL, "سلام")).isEqualTo(DeliveryResult.SENT);
        assertThat(client.token).isEqualTo("shared-token");
    }

    @Test
    void nullSchoolUsesSharedLinkWithoutLookingUpBots() {
        when(links.findByUser(BotRef.SHARED, 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(null, 7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT, 200);

        assertThat(channel(client).deliver(7L, null, "سلام")).isEqualTo(DeliveryResult.SENT);
        verify(bots, never()).findBySchoolIdAndStatus(any(), any());
    }

    @Test
    void unauthorizedFromSchoolBotDisablesIt() {
        activeBot();
        when(links.findByUser(BotRef.school(BOT, SCHOOL), 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(BOT, 7L, 777L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.UNDELIVERABLE, 401);

        assertThat(channel(client).deliver(7L, SCHOOL, "سلام")).isEqualTo(DeliveryResult.UNDELIVERABLE);
        verify(bots).markStatus(BOT, SchoolBotStatus.DISABLED, BotTexts.BOT_TOKEN_INVALID, NOW);
    }

    @Test
    void otherClientErrorsFromSchoolBotDoNotDisableIt() {
        activeBot();
        when(links.findByUser(BotRef.school(BOT, SCHOOL), 7L))
                .thenReturn(Optional.of(new BaleLinkEntity(BOT, 7L, 777L, Instant.now())));

        assertThat(channel(new RecordingClient(DeliveryResult.UNDELIVERABLE, 403)).deliver(7L, SCHOOL, "سلام"))
                .isEqualTo(DeliveryResult.UNDELIVERABLE);
        verify(bots, never()).markStatus(any(), any(), anyString(), any());
    }
}
