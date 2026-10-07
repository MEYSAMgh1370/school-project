package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * کانال بله را بدون Spring و Docker می‌آزماید: بدونِ لینک false (و بدون هیچ
 * تماسی با بله)، با لینک پیام به chatId لینک‌شده می‌رود و نتیجه‌ی کلاینت
 * برگردانده می‌شود.
 */
class BaleChannelTest {

    private static class RecordingClient extends BaleClient {
        private final DeliveryResult result;
        Long chatId;
        String text;

        RecordingClient(DeliveryResult result) {
            super("http://localhost:0", "");
            this.result = result;
        }

        @Override
        DeliveryResult deliver(Long chatId, String text) {
            this.chatId = chatId;
            this.text = text;
            return result;
        }
    }

    private final BaleLinkRepository links = mock(BaleLinkRepository.class);

    @Test
    void unlinkedUserIsUnreachableAndNothingIsSent() {
        when(links.findByUserId(7L)).thenReturn(Optional.empty());
        RecordingClient client = new RecordingClient(DeliveryResult.SENT);

        assertThat(new BaleChannel(links, client).send(7L, "سلام")).isFalse();
        assertThat(client.chatId).isNull();
    }

    @Test
    void linkedUserGetsTheMessageOnTheirLinkedChat() {
        when(links.findByUserId(7L)).thenReturn(Optional.of(new BaleLinkEntity(7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(DeliveryResult.SENT);

        assertThat(new BaleChannel(links, client).send(7L, "سلام")).isTrue();
        assertThat(client.chatId).isEqualTo(555L);
        assertThat(client.text).isEqualTo("سلام");
    }

    @Test
    void aFailedSendIsReportedAsNotDelivered() {
        when(links.findByUserId(7L)).thenReturn(Optional.of(new BaleLinkEntity(7L, 555L, Instant.now())));

        assertThat(new BaleChannel(links, new RecordingClient(DeliveryResult.TEMPORARY_FAILURE)).send(7L, "سلام")).isFalse();
    }

    @Test
    void theDetailedResultDistinguishesNotLinkedFromAClientFailure() {
        when(links.findByUserId(7L)).thenReturn(Optional.empty());
        assertThat(new BaleChannel(links, new RecordingClient(DeliveryResult.SENT)).deliver(7L, "سلام"))
                .isEqualTo(DeliveryResult.NOT_LINKED);

        when(links.findByUserId(8L)).thenReturn(Optional.of(new BaleLinkEntity(8L, 556L, Instant.now())));
        assertThat(new BaleChannel(links, new RecordingClient(DeliveryResult.TEMPORARY_FAILURE)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.TEMPORARY_FAILURE);
        assertThat(new BaleChannel(links, new RecordingClient(DeliveryResult.UNDELIVERABLE)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(new BaleChannel(links, new RecordingClient(DeliveryResult.SENT)).deliver(8L, "سلام"))
                .isEqualTo(DeliveryResult.SENT);
    }
}
