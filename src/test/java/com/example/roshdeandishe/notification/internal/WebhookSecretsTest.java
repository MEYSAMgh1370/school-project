package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSecretsTest {

    @Test
    void newSecretHas256BitsAndIsUrlSafe() {
        String s = WebhookSecrets.newSecret();
        assertThat(Base64.getUrlDecoder().decode(s)).hasSize(32);
        assertThat(s).matches("[A-Za-z0-9_-]+");
        assertThat(WebhookSecrets.newSecret()).isNotEqualTo(s);
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(WebhookSecrets.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
