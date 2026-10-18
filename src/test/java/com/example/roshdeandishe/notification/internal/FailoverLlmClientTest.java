package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailoverLlmClientTest {

    private static final ClassificationRequest REQUEST = new ClassificationRequest("x", Map.of(), List.of());

    private static ClassificationAnswer answer(String model, double confidence) {
        return new ClassificationAnswer("GRADES", confidence, null, 0, model);
    }

    private static LlmClient failing(String kind) {
        return r -> {
            throw new LlmException(kind);
        };
    }

    @Test
    void usesPrimaryWhenItAnswers() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        FailoverLlmClient client = new FailoverLlmClient(r -> answer("p", 0.9), r -> {
            fallbackCalls.incrementAndGet();
            return answer("f", 1);
        });

        assertThat(client.classify(REQUEST).model()).isEqualTo("p");
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void fallsBackOnPrimaryFailure() {
        FailoverLlmClient client = new FailoverLlmClient(failing("timeout"), r -> answer("f", 1));

        assertThat(client.classify(REQUEST).model()).isEqualTo("f");
    }

    @Test
    void lowConfidencePrimaryAnswerIsReturnedWithoutFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        FailoverLlmClient client = new FailoverLlmClient(r -> answer("p", 0.1), r -> {
            fallbackCalls.incrementAndGet();
            return answer("f", 1);
        });

        assertThat(client.classify(REQUEST).intentConfidence()).isEqualTo(0.1);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void bothFailingPropagates() {
        FailoverLlmClient client = new FailoverLlmClient(failing("timeout"), failing("http 503"));

        assertThatThrownBy(() -> client.classify(REQUEST))
                .isInstanceOf(LlmException.class).hasMessage("http 503");
    }

    @Test
    void productionConstructorBuildsWithoutNetwork() {
        LlmSettings settings = new LlmSettings(new org.springframework.mock.env.MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.base-url", "http://liara.test")
                .withProperty("roshdeandishe.bot.llm.api-key", "k"));

        assertThat(new FailoverLlmClient(settings)).isNotNull();
    }
}
