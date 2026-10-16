package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * پارس Updateهای ورودی وب‌هوک بله - اینجا callback_query (کلیک روی دکمه‌ی اینلاین).
 */
class BaleUpdateParsingTest {

    @Test
    void parsesCallbackQueryWithItsChatAndData() throws JsonProcessingException {
        BaleUpdate u = new ObjectMapper().readValue("""
                {"update_id":7,"callback_query":{"id":"cb1","data":"X:1","message":{"message_id":3,"chat":{"id":42}}}}""",
                BaleUpdate.class);

        assertThat(u.callbackQuery().id()).isEqualTo("cb1");
        assertThat(u.callbackQuery().data()).isEqualTo("X:1");
        assertThat(u.callbackQuery().message().chat().id()).isEqualTo(42L);
        assertThat(u.message()).isNull();
    }
}
