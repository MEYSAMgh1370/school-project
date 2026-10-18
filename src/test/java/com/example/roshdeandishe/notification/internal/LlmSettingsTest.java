package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmSettingsTest {

    @Test
    void disabledWithoutUrlOrKey() {
        assertThat(new LlmSettings(new MockEnvironment()).enabled()).isFalse();
        assertThat(new LlmSettings(new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.enabled", "true")
                .withProperty("roshdeandishe.bot.llm.api-key", "k")).enabled()).isFalse();
        assertThat(new LlmSettings(new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.enabled", "true")
                .withProperty("roshdeandishe.bot.llm.base-url", "http://x")).enabled()).isFalse();
        assertThat(new LlmSettings(new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.base-url", "http://x")
                .withProperty("roshdeandishe.bot.llm.api-key", "k")).enabled()).isFalse();
        assertThat(new LlmSettings(new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.enabled", "true")
                .withProperty("roshdeandishe.bot.llm.base-url", "http://x")
                .withProperty("roshdeandishe.bot.llm.api-key", "k")).enabled()).isTrue();
    }

    @Test
    void dailyLimitFallsBackToDefault() {
        LlmSettings s = new LlmSettings(new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.daily-limit.default", "30")
                .withProperty("roshdeandishe.bot.llm.daily-limit.SCHOOL_ADMIN", "100"));

        assertThat(s.dailyLimitFor("SCHOOL_ADMIN")).isEqualTo(100);
        assertThat(s.dailyLimitFor("PARENT")).isEqualTo(30);
        assertThat(new LlmSettings(new MockEnvironment()).dailyLimitFor("PARENT")).isEqualTo(30);
    }

    @Test
    void applicationYamlValuesResolveIncludingUpperCaseRoleKeys() throws Exception {
        MockEnvironment env = new MockEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(env.getPropertySources()::addLast);

        assertThat(env.getProperty("roshdeandishe.bot.llm.daily-limit.TEACHER")).isEqualTo("30");
        LlmSettings s = new LlmSettings(env);
        assertThat(s.dailyLimitFor("TEACHER")).isEqualTo(30);
        assertThat(s.dailyLimitFor("SCHOOL_ADMIN")).isEqualTo(100);
        assertThat(s.dailyLimitFor("STUDENT")).isEqualTo(30);
        assertThat(s.primaryModel()).isEqualTo("typesafe/jev-1.13");
        assertThat(s.fallbackModel()).isEqualTo("qwen/qwen3-32b");
        assertThat(s.decisionPath()).isEqualTo("/systemone");
        assertThat(s.primaryTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(s.fallbackTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(s.minConfidence()).isEqualTo(0.7);
        assertThat(s.maxTextLength()).isEqualTo(500);
        assertThat(s.dailyCapTotal()).isZero();
        assertThat(s.enabled()).isFalse();
    }
}
