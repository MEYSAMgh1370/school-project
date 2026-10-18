package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.internal.CallbackCodec.Decoded;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.example.roshdeandishe.notification.internal.BotIntent.GRADES;
import static com.example.roshdeandishe.notification.internal.BotIntent.TEACHER_ROLL;
import static org.assertj.core.api.Assertions.assertThat;

class CallbackCodecTest {

    private static BotTarget student(long id) {
        return new BotTarget.Student(1L, id, 9L, null, "علی", "مدرسه", false);
    }

    private static BotTarget school(long id) {
        return new BotTarget.School(id, "مدرسه");
    }

    @Test
    void roundTrips() {
        assertThat(CallbackCodec.decode(CallbackCodec.encode(GRADES, student(123L))))
                .contains(new Decoded(GRADES, 123L));
        assertThat(CallbackCodec.encode(GRADES, student(123L))).isEqualTo("g:123");
    }

    @Test
    void rejectsMalformed() {
        for (String bad : new String[]{"", "g", "g:", "g:x", "zz:1", ":1", "g:1:2", "g:-1", null}) {
            assertThat(CallbackCodec.decode(bad)).as("input %s", bad).isEmpty();
        }
    }

    @Test
    void staysWithin64Bytes() {
        String data = CallbackCodec.encode(TEACHER_ROLL, school(Long.MAX_VALUE));
        assertThat(data.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
    }

    @Test
    void everyIntentHasUniqueCodeAndButton() {
        for (BotIntent intent : BotIntent.values()) {
            assertThat(BotIntent.byCode(intent.code())).contains(intent);
            assertThat(BotIntent.byButton(intent.button())).contains(intent);
        }
        assertThat(BotIntent.byButton("x")).isEmpty();
        assertThat(BotIntent.byCode("x")).isEmpty();
    }
}
