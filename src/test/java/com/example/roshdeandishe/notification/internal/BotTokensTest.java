package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotTokensTest {

    private final SchoolBotRepository bots = mock(SchoolBotRepository.class);
    private final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final BotTokens tokens = new BotTokens("shared:token", bots, cipher);

    @Test
    void sharedRefUsesConfiguredToken() {
        assertThat(tokens.tokenFor(BotRef.SHARED)).isEqualTo("shared:token");
    }

    @Test
    void schoolRefDecryptsStoredToken() {
        TokenCipher.Encrypted enc = cipher.encrypt("999:zz");
        SchoolBotEntity entity = mock(SchoolBotEntity.class);
        when(entity.getTokenCiphertext()).thenReturn(enc.ciphertext());
        when(entity.getTokenKeyVersion()).thenReturn(enc.keyVersion());
        when(bots.findById(5L)).thenReturn(Optional.of(entity));

        assertThat(tokens.tokenFor(BotRef.school(5L, 9L))).isEqualTo("999:zz");
    }

    @Test
    void unknownSchoolBotGivesBlank() {
        when(bots.findById(5L)).thenReturn(Optional.empty());

        assertThat(tokens.tokenFor(BotRef.school(5L, 9L))).isEmpty();
    }
}
