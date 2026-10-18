package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * مدلِ اصلی را امتحان می‌کند و فقط با {@link LlmException} سراغ مدلِ پشتیبان می‌رود؛ شکستِ دوم
 * بالا می‌رود. پاسخِ کم‌اطمینانِ مدلِ اصلی شکست نیست و بدون پشتیبان برگردانده می‌شود.
 */
@Primary
@Component
class FailoverLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(FailoverLlmClient.class);

    private final LlmClient primary;
    private final LlmClient fallback;

    @Autowired
    FailoverLlmClient(LlmSettings settings, RestClient.Builder builder) {
        this(new DecisionModelClient(withTimeout(builder, settings.primaryTimeout()), settings.baseUrl(),
                        settings.apiKey(), settings.primaryModel(), settings.decisionPath()),
                new OpenAiCompatibleClient(withTimeout(builder, settings.fallbackTimeout()), settings.baseUrl(),
                        settings.apiKey(), settings.fallbackModel()));
    }

    FailoverLlmClient(LlmClient primary, LlmClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ClassificationAnswer classify(ClassificationRequest request) {
        try {
            return primary.classify(request);
        } catch (LlmException e) {
            log.warn("مدل اصلی پاسخ نداد ({}); مدل پشتیبان امتحان می‌شود.", e.getMessage());
            return fallback.classify(request);
        }
    }

    private static RestClient.Builder withTimeout(RestClient.Builder builder, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return builder.clone().requestFactory(factory);
    }
}
