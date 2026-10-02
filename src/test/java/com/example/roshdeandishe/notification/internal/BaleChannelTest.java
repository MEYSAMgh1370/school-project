package com.example.roshdeandishe.notification.internal;

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
        private final boolean result;
        Long chatId;
        String text;

        RecordingClient(boolean result) {
            super("http://localhost:0", "");
            this.result = result;
        }

        @Override
        boolean sendMessage(Long chatId, String text) {
            this.chatId = chatId;
            this.text = text;
            return result;
        }
    }

    private final BaleLinkRepository links = mock(BaleLinkRepository.class);

    @Test
    void unlinkedUserIsUnreachableAndNothingIsSent() {
        when(links.findByUserId(7L)).thenReturn(Optional.empty());
        RecordingClient client = new RecordingClient(true);

        assertThat(new BaleChannel(links, client).send(7L, "سلام")).isFalse();
        assertThat(client.chatId).isNull();
    }

    @Test
    void linkedUserGetsTheMessageOnTheirLinkedChat() {
        when(links.findByUserId(7L)).thenReturn(Optional.of(new BaleLinkEntity(7L, 555L, Instant.now())));
        RecordingClient client = new RecordingClient(true);

        assertThat(new BaleChannel(links, client).send(7L, "سلام")).isTrue();
        assertThat(client.chatId).isEqualTo(555L);
        assertThat(client.text).isEqualTo("سلام");
    }

    @Test
    void aFailedSendIsReportedAsNotDelivered() {
        when(links.findByUserId(7L)).thenReturn(Optional.of(new BaleLinkEntity(7L, 555L, Instant.now())));

        assertThat(new BaleChannel(links, new RecordingClient(false)).send(7L, "سلام")).isFalse();
    }
}
