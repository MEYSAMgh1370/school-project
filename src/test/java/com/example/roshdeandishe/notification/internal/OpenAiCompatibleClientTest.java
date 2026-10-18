package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleClientTest {

    private static final String MODEL = "qwen/qwen3-32b";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiCompatibleClient client =
            new OpenAiCompatibleClient(builder, "http://liara.test/v1", "k", MODEL);

    private static Map<String, String> options() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("EVENTS", "رویدادها");
        m.put("NONE", "هیچ‌کدام");
        return m;
    }

    private static String reply(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + new com.fasterxml.jackson.databind.ObjectMapper()
                .valueToTree(content).toString() + "}}]}";
    }

    @Test
    void stripsThinkingAndParsesJson() {
        server.expect(requestTo("http://liara.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer k"))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.temperature").value(0))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("- EVENTS: رویدادها")))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("- [فرزند_۱]")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(endsWith(" /no_think")))
                .andRespond(withSuccess(
                        reply("<think>فکر {می‌کنم}</think>\n{\"intent\":\"EVENTS\",\"target\":\"NONE\"}"),
                        MediaType.APPLICATION_JSON));

        ClassificationAnswer a = client.classify(
                new ClassificationRequest("برنامه ها", options(), List.of("[فرزند_۱]", "NONE")));

        assertThat(a).isEqualTo(new ClassificationAnswer("EVENTS", 1.0, "NONE", 1.0, MODEL));
        server.verify();
    }

    @Test
    void noJsonObjectIsLlmException() {
        server.expect(requestTo("http://liara.test/v1/chat/completions"))
                .andRespond(withSuccess(reply("نمی‌دانم"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.classify(new ClassificationRequest("x", options(), List.of())))
                .isInstanceOf(LlmException.class).hasMessage("unparsable");
    }
}
