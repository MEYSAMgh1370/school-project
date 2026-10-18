package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * آداپتورِ مدلِ پشتیبان (سازگار با OpenAI روی لیارا): گزینه‌ها در پرامپتِ سیستمی می‌آیند و پاسخ یک
 * شیء JSON است. این API اطمینان نمی‌دهد، پس هر دو اطمینان {@code 1.0} گزارش می‌شوند.
 */
final class OpenAiCompatibleClient implements LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern THINK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    OpenAiCompatibleClient(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
        this.restClient = builder.clone().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ClassificationAnswer classify(ClassificationRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt(request));
        messages.addObject().put("role", "user").put("content", request.text() + " /no_think");

        JsonNode root = LlmHttp.parse(LlmHttp.post(restClient, "/chat/completions", apiKey, body));
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new LlmException("unparsable");
        }
        JsonNode answer = firstObject(THINK.matcher(content.asText()).replaceAll(""));
        JsonNode intent = answer.path("intent");
        if (!intent.isTextual() || intent.asText().isBlank()) {
            throw new LlmException("unparsable");
        }
        String target = null;
        double targetConfidence = 0;
        if (!request.targetLabels().isEmpty()) {
            JsonNode t = answer.path("target");
            if (!t.isTextual()) {
                throw new LlmException("unparsable");
            }
            target = t.asText();
            targetConfidence = 1.0;
        }
        return new ClassificationAnswer(intent.asText(), 1.0, target, targetConfidence, model);
    }

    private static String systemPrompt(ClassificationRequest request) {
        StringBuilder sb = new StringBuilder(LlmPrompts.instructions());
        sb.append("\n\nIntents:\n");
        for (Map.Entry<String, String> e : request.intentOptions().entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        if (!request.targetLabels().isEmpty()) {
            sb.append("\nTargets:\n");
            request.targetLabels().forEach(label -> sb.append("- ").append(label).append('\n'));
            sb.append("\nReply with exactly one JSON object: {\"intent\":\"NAME\",\"target\":\"label-or-NONE\"}");
        } else {
            sb.append("\nReply with exactly one JSON object: {\"intent\":\"NAME\"}");
        }
        return sb.toString();
    }

    /** اولین شیءِ {@code {…}} در متن. */
    private static JsonNode firstObject(String text) {
        int start = text.indexOf('{');
        int end = start < 0 ? -1 : text.indexOf('}', start);
        if (end < 0) {
            throw new LlmException("unparsable");
        }
        try {
            JsonNode node = JSON.readTree(text.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new LlmException("unparsable");
            }
            return node;
        } catch (IOException e) {
            throw new LlmException("unparsable");
        }
    }
}
