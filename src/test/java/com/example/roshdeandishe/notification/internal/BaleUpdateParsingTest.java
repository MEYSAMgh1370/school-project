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

    @Test
    void parsesSenderChatTypeAndContactUserId() throws JsonProcessingException {
        BaleUpdate u = new ObjectMapper().readValue("""
                {"update_id":8,"message":{"message_id":3,"from":{"id":4503599627370495,"is_bot":false,"first_name":"t"},
                "chat":{"id":42,"type":"private"},"contact":{"phone_number":"0912","first_name":"t","user_id":99}}}""",
                BaleUpdate.class);

        assertThat(u.message().from().id()).isEqualTo(4503599627370495L);
        assertThat(u.message().chat().type()).isEqualTo("private");
        assertThat(u.message().contact().userId()).isEqualTo(99L);
    }

    @Test
    void missingUserIdFromAndChatTypeParseAsNull() throws JsonProcessingException {
        BaleUpdate u = new ObjectMapper().readValue("""
                {"update_id":9,"message":{"message_id":3,"chat":{"id":42},"contact":{"phone_number":"0912"}}}""",
                BaleUpdate.class);

        assertThat(u.message().from()).isNull();
        assertThat(u.message().chat().type()).isNull();
        assertThat(u.message().contact().userId()).isNull();
    }
}
