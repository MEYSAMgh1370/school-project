package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static String key(int bytes) {
        byte[] k = new byte[bytes];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        TokenCipher cipher = new TokenCipher(key(32));
        TokenCipher.Encrypted enc = cipher.encrypt("123:abc");
        assertThat(enc.keyVersion()).isEqualTo(1);
        assertThat(cipher.decrypt(enc.ciphertext(), enc.keyVersion())).isEqualTo("123:abc");
    }

    @Test
    void sameTokenEncryptsDifferentlyEachTime() {
        TokenCipher cipher = new TokenCipher(key(32));
        assertThat(cipher.encrypt("123:abc").ciphertext()).isNotEqualTo(cipher.encrypt("123:abc").ciphertext());
    }

    @Test
    void tamperedCiphertextIsRejected() {
        TokenCipher cipher = new TokenCipher(key(32));
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("123:abc").ciphertext());
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankKeyIsUnavailableAndRefusesToEncrypt() {
        TokenCipher cipher = new TokenCipher("");
        assertThat(cipher.available()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("123:abc")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyOfWrongLengthFailsAtConstruction() {
        String bad = key(16);
        assertThatThrownBy(() -> new TokenCipher(bad)).isInstanceOf(IllegalStateException.class);
    }
}
