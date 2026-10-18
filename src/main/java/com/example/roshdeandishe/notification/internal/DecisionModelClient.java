package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * آداپتورِ API تصمیمِ TypeSafe (مدل اصلی روی لیارا): پرسشِ choice با معیارها می‌فرستد و
 * گزینه و اطمینانِ هر پرسش را می‌خواند. مدل هیچ داده‌ی واقعی‌ای نمی‌بیند.
 */
final class DecisionModelClient implements LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String path;

    DecisionModelClient(RestClient.Builder builder, String baseUrl, String apiKey, String model, String path) {
        this.restClient = builder.clone().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.path = path;
    }

    @Override
    public ClassificationAnswer classify(ClassificationRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("state", request.text());
        ObjectNode questions = body.putObject("questions");
        ObjectNode intent = questions.putObject("intent");
        intent.put("type", "choice");
        intent.put("instructions", LlmPrompts.instructions());
        ObjectNode intentCriteria = intent.putObject("criteria");
        for (Map.Entry<String, String> e : request.intentOptions().entrySet()) {
            intentCriteria.put(e.getKey(), e.getValue());
        }
        boolean askTarget = !request.targetLabels().isEmpty();
        if (askTarget) {
            ObjectNode target = questions.putObject("target");
            target.put("type", "choice");
            target.put("instructions", LlmPrompts.instructions());
            ObjectNode targetCriteria = target.putObject("criteria");
            request.targetLabels().forEach(label -> targetCriteria.putNull(label));
        }

        JsonNode answers = LlmHttp.parse(LlmHttp.post(restClient, path, apiKey, body)).path("answers");
        JsonNode intentAnswer = answers.path("intent");
        if (!intentAnswer.path("choice").isTextual()) {
            throw new LlmException("unparsable");
        }
        String targetChoice = null;
        double targetConfidence = 0;
        if (askTarget) {
            JsonNode targetAnswer = answers.path("target");
            if (!targetAnswer.path("choice").isTextual()) {
                throw new LlmException("unparsable");
            }
            targetChoice = targetAnswer.path("choice").asText();
            targetConfidence = targetAnswer.path("confidence").asDouble(0);
        }
        return new ClassificationAnswer(intentAnswer.path("choice").asText(),
                intentAnswer.path("confidence").asDouble(0), targetChoice, targetConfidence, model);
    }
}

/** کمکِ مشترکِ آداپتورها: فراخوانیِ POST و تبدیلِ هر شکست به {@link LlmException} که فقط نوعِ خطا را می‌گوید. */
final class LlmHttp {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LlmHttp() {
    }

    static String post(RestClient client, String path, String apiKey, JsonNode body) {
        try {
            String response = client.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new LlmException("unparsable");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new LlmException("http " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new LlmException(isTimeout(e) ? "timeout" : "connection");
        }
    }

    static JsonNode parse(String response) {
        try {
            JsonNode root = JSON.readTree(response);
            if (root == null || !root.isObject()) {
                throw new LlmException("unparsable");
            }
            return root;
        } catch (IOException e) {
            throw new LlmException("unparsable");
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
