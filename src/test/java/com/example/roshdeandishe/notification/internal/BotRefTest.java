package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotRefTest {

    @Test
    void sharedHasNoIds() {
        assertThat(BotRef.SHARED.isShared()).isTrue();
        assertThat(BotRef.SHARED.botId()).isNull();
        assertThat(BotRef.SHARED.schoolId()).isNull();
        BotRef ref = BotRef.school(5, 7);
        assertThat(ref.isShared()).isFalse();
        assertThat(ref.botId()).isEqualTo(5L);
        assertThat(ref.schoolId()).isEqualTo(7L);
    }
}
