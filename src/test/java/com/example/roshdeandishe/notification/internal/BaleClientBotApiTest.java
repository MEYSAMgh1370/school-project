package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;
import com.example.roshdeandishe.notification.internal.BaleClient.BotInfo;
import com.example.roshdeandishe.notification.internal.BaleClient.BotInfoResult;
import com.example.roshdeandishe.notification.internal.BaleClient.SendResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** getMe/setWebhook/deleteWebhook و deliverDetailed روی سرور HTTP محلی، بدون Docker. */
class BaleClientBotApiTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicInteger deleteWebhookHits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            if (exchange.getRequestURI().getPath().endsWith("/deleteWebhook")) {
                deleteWebhookHits.incrementAndGet();
            }
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BaleClient client() {
        return new BaleClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void getMeReturnsBotIdAndUsername() {
        responseBody.set("{\"ok\":true,\"result\":{\"id\":42,\"is_bot\":true,\"first_name\":\"x\",\"username\":\"school_bot\"}}");

        assertThat(client().getMe("T")).isEqualTo(new BotInfoResult.Found(new BotInfo(42, "school_bot")));
        assertThat(lastPath.get()).isEqualTo("/botT/getMe");
    }

    @Test
    void getMeWith401Or404IsInvalidToken() {
        for (int code : new int[]{401, 404}) {
            status.set(code);
            assertThat(client().getMe("T")).as("HTTP " + code).isInstanceOf(BotInfoResult.InvalidToken.class);
        }
    }

    @Test
    void getMeWithOkFalseIsInvalidToken() {
        responseBody.set("{\"ok\":false}");

        assertThat(client().getMe("T")).isInstanceOf(BotInfoResult.InvalidToken.class);
    }

    @Test
    void getMeWith502Or429IsUnreachable() {
        for (int code : new int[]{502, 429}) {
            status.set(code);
            assertThat(client().getMe("T")).as("HTTP " + code).isInstanceOf(BotInfoResult.Unreachable.class);
        }
    }

    @Test
    void getMeOnAnUnreachableServerIsUnreachable() {
        BaleClient client = client();
        server.stop(0);

        assertThat(client.getMe("T")).isInstanceOf(BotInfoResult.Unreachable.class);
    }

    @Test
    void setWebhookPostsTheUrl() {
        responseBody.set("{\"ok\":true,\"result\":true}");

        assertThat(client().setWebhook("T", "https://x/api/bale/webhook/s")).isTrue();
        assertThat(lastPath.get()).isEqualTo("/botT/setWebhook");
        assertThat(lastBody.get()).contains("\"url\":\"https://x/api/bale/webhook/s\"");
    }

    @Test
    void setWebhookFalseWhenOkFalse() {
        responseBody.set("{\"ok\":false}");

        assertThat(client().setWebhook("T", "https://x/api/bale/webhook/s")).isFalse();
    }

    @Test
    void setWebhookFalseOnHttpError() {
        status.set(500);

        assertThat(client().setWebhook("T", "https://x/api/bale/webhook/s")).isFalse();
    }

    @Test
    void deleteWebhookCallsTheMethod() {
        responseBody.set("{\"ok\":true,\"result\":true}");

        assertThat(client().deleteWebhook("T")).isTrue();
        assertThat(deleteWebhookHits.get()).isEqualTo(1);
    }

    @Test
    void deliverDetailedReportsHttpStatus() {
        status.set(401);
        assertThat(client().deliverDetailed("T", 1L, "x")).isEqualTo(new SendResult(DeliveryResult.UNDELIVERABLE, 401));

        status.set(200);
        assertThat(client().deliverDetailed("T", 1L, "x")).isEqualTo(new SendResult(DeliveryResult.SENT, 200));

        BaleClient client = client();
        server.stop(0);
        assertThat(client.deliverDetailed("T", 1L, "x")).isEqualTo(new SendResult(DeliveryResult.TEMPORARY_FAILURE, 0));
    }
}
