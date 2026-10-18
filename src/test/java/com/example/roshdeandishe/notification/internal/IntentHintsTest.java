package com.example.roshdeandishe.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentHintsTest {
    @Test
    void everyIntentHasAHintAndNoneIsLast() {
        List<String> expected = new ArrayList<>(Arrays.stream(BotIntent.values()).map(Enum::name).toList());
        expected.add("NONE");
        assertThat(LlmPrompts.intentHints().keySet()).containsExactlyElementsOf(expected);
        assertThat(LlmPrompts.intentHints().values()).allMatch(h -> !h.isBlank());
        assertThat(LlmPrompts.instructions()).contains("NONE");
    }
}
