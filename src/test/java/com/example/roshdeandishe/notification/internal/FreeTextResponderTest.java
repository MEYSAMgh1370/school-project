package com.example.roshdeandishe.notification.internal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeTextResponderTest {

    private BaleUpdateHandler handler;
    private LlmClient llm;
    private LlmQuota quota;
    private BaleClient client;
    private FreeTextResponder responder;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        handler = mock(BaleUpdateHandler.class);
        llm = mock(LlmClient.class);
        quota = mock(LlmQuota.class);
        client = mock(BaleClient.class);
        BotTokens tokens = mock(BotTokens.class);
        when(tokens.tokenFor(any())).thenReturn("tok");
        when(quota.tryAcquire(anyLong(), anyCollection())).thenReturn(true);
        responder = new FreeTextResponder(handler, llm, quota, new LlmSettings(new MockEnvironment()), client, tokens);

        logger = (Logger) LoggerFactory.getLogger(FreeTextResponder.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static FreeTextRequest request(Map<String, Long> labels) {
        return new FreeTextRequest(BotRef.SHARED, 42L, 7L, List.of("PARENT"), "نمره های [فرزند_۱] چی شد؟",
                EnumSet.of(BotIntent.GRADES, BotIntent.INVOICES), labels);
    }

    private static Map<String, Long> twoWards() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("[فرزند_۱]", 555L);
        m.put("[فرزند_۲]", 556L);
        return m;
    }

    @Test
    void quotaExhaustedSkipsModel() {
        when(quota.tryAcquire(anyLong(), anyCollection())).thenReturn(false);
        FreeTextRequest request = request(Map.of("[فرزند_۱]", 555L));

        responder.respond(request);

        verify(handler).answerLimitReached(request);
        verify(llm, never()).classify(any());
        verify(handler, never()).answerFreeText(any(), any());
    }

    @Test
    void llmFailureAnswersMenu() {
        when(llm.classify(any())).thenThrow(new LlmException("timeout"));
        FreeTextRequest request = request(Map.of("[فرزند_۱]", 555L));

        responder.respond(request);

        verify(handler).answerFreeText(request, Optional.empty());
        assertThat(logs.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("outcome=FALLBACK"));
    }

    @Test
    void unexpectedExceptionAnswersMenuWithErrorOutcome() {
        when(llm.classify(any())).thenThrow(new IllegalStateException("boom"));
        FreeTextRequest request = request(Map.of("[فرزند_۱]", 555L));

        responder.respond(request);

        verify(handler).answerFreeText(request, Optional.empty());
        assertThat(logs.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("outcome=ERROR"));
    }

    @Test
    void typingSentBeforeClassify() {
        when(llm.classify(any())).thenReturn(new ClassificationAnswer("GRADES", 0.9, null, 0, "m"));

        responder.respond(request(Map.of("[فرزند_۱]", 555L)));

        InOrder order = inOrder(client, llm);
        order.verify(client).sendChatAction("tok", 42L, "typing");
        order.verify(llm).classify(any());
    }

    @Test
    void singleLabelSendsNoTargetQuestion() {
        when(llm.classify(any())).thenReturn(new ClassificationAnswer("GRADES", 0.9, null, 0, "m"));
        FreeTextRequest request = request(Map.of("[فرزند_۱]", 555L));

        responder.respond(request);

        ArgumentCaptor<ClassificationRequest> sent = ArgumentCaptor.forClass(ClassificationRequest.class);
        verify(llm).classify(sent.capture());
        assertThat(sent.getValue().targetLabels()).isEmpty();
        assertThat(sent.getValue().intentOptions().keySet()).containsExactly("GRADES", "INVOICES", "NONE");
        assertThat(sent.getValue().text()).isEqualTo(request.text());
        verify(handler).answerFreeText(request,
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, null)));
    }

    @Test
    void twoLabelsAskTheTargetQuestionAndPassTheChosenLabel() {
        when(llm.classify(any())).thenReturn(new ClassificationAnswer("GRADES", 0.9, "[فرزند_۲]", 0.9, "m"));
        FreeTextRequest request = request(twoWards());

        responder.respond(request);

        ArgumentCaptor<ClassificationRequest> sent = ArgumentCaptor.forClass(ClassificationRequest.class);
        verify(llm).classify(sent.capture());
        assertThat(sent.getValue().targetLabels()).containsExactly("[فرزند_۱]", "[فرزند_۲]", "NONE");
        verify(handler).answerFreeText(request,
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۲]")));
    }

    @Test
    void disallowedIntentFallsBackToMenu() {
        when(llm.classify(any())).thenReturn(new ClassificationAnswer("TEACHER_ROLL", 0.99, null, 0, "m"));
        FreeTextRequest request = request(Map.of("[فرزند_۱]", 555L));

        responder.respond(request);

        verify(handler).answerFreeText(request, Optional.empty());
    }

    @Test
    void logsNeverContainText() {
        when(llm.classify(any())).thenReturn(new ClassificationAnswer("GRADES", 0.9, "[فرزند_۱]", 0.9, "m"));

        responder.respond(request(twoWards()));

        assertThat(logs.list).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).contains("intent=GRADES", "outcome=OK", "model=m", "latencyMs=");
            assertThat(e.getFormattedMessage()).doesNotContain("نمره", "فرزند", "555");
        });
    }
}
