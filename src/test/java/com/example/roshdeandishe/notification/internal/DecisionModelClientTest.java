package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DecisionModelClientTest {

    private static final String MODEL = "typesafe/jev-1.13";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DecisionModelClient client =
            new DecisionModelClient(builder, "http://liara.test", "k", MODEL, "/systemone");

    private static Map<String, String> options() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("GRADES", "نمره");
        m.put("NONE", "هیچ‌کدام");
        return m;
    }

    @Test
    void sendsChoiceQuestionsAndReadsConfidence() {
        server.expect(requestTo("http://liara.test/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer k"))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.state").value("نمره های [فرزند_۱]"))
                .andExpect(jsonPath("$.questions.intent.type").value("choice"))
                .andExpect(jsonPath("$.questions.intent.criteria.GRADES").value("نمره"))
                .andExpect(jsonPath("$.questions.target.criteria['[فرزند_۱]']").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.questions.target.criteria").value(org.hamcrest.Matchers.hasKey("[فرزند_۱]")))
                .andRespond(withSuccess("""
                        {"answers":{"intent":{"type":"choice","choice":"GRADES","confidence":0.91},
                        "target":{"type":"choice","choice":"[فرزند_۱]","confidence":0.88}}}
                        """, MediaType.APPLICATION_JSON));

        ClassificationAnswer a = client.classify(
                new ClassificationRequest("نمره های [فرزند_۱]", options(), List.of("[فرزند_۱]", "NONE")));

        assertThat(a).isEqualTo(new ClassificationAnswer("GRADES", 0.91, "[فرزند_۱]", 0.88, MODEL));
        server.verify();
    }

    @Test
    void omitsTargetQuestionWhenNoLabels() {
        server.expect(requestTo("http://liara.test/systemone"))
                .andExpect(jsonPath("$.questions.target").doesNotExist())
                .andRespond(withSuccess("""
                        {"answers":{"intent":{"type":"choice","choice":"GRADES","confidence":0.9}}}
                        """, MediaType.APPLICATION_JSON));

        ClassificationAnswer a = client.classify(new ClassificationRequest("نمره", options(), List.of()));

        assertThat(a.intent()).isEqualTo("GRADES");
        assertThat(a.target()).isNull();
        server.verify();
    }

    @Test
    void httpErrorAndGarbageBecomeLlmException() {
        server.expect(requestTo("http://liara.test/systemone")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.classify(new ClassificationRequest("x", options(), List.of())))
                .isInstanceOf(LlmException.class).hasMessage("http 503");

        server.reset();
        server.expect(requestTo("http://liara.test/systemone"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.classify(new ClassificationRequest("x", options(), List.of())))
                .isInstanceOf(LlmException.class).hasMessage("unparsable");
    }
}
