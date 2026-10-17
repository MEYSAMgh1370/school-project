package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BotResolverTest {

    private final SchoolBotRepository bots = mock(SchoolBotRepository.class);

    @Test
    void sharedSecretResolvesToSharedBot() {
        assertThat(new BotResolver("shared-s", bots).resolve("shared-s")).contains(BotRef.SHARED);
        verifyNoInteractions(bots);
    }

    @Test
    void schoolSecretResolvesToItsBot() {
        SchoolBotEntity entity = new SchoolBotEntity();
        entity.setId(3L);
        entity.setSchoolId(7L);
        when(bots.findByWebhookSecretHashAndStatus(WebhookSecrets.hash("school-s"), SchoolBotStatus.ACTIVE))
                .thenReturn(Optional.of(entity));

        assertThat(new BotResolver("shared-s", bots).resolve("school-s")).contains(BotRef.school(3, 7));
    }

    @Test
    void disabledBotSecretIsUnknown() {
        when(bots.findByWebhookSecretHashAndStatus(WebhookSecrets.hash("school-s"), SchoolBotStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(new BotResolver("shared-s", bots).resolve("school-s")).isEmpty();
    }

    @Test
    void blankSharedSecretMatchesNothing() {
        BotResolver resolver = new BotResolver("", bots);

        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        verifyNoInteractions(bots);
    }
}
