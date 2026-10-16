package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.internal.BaleClient.InlineButton;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * کیبورد اینلاین و پاسخ به callback_query روی یک سرور HTTP واقعیِ محلی، بدون Docker؛ مسیر و بدنه‌ی درخواست ثبت می‌شود.
 */
class BaleClientInlineKeyboardTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BaleClient client(String token) {
        return new BaleClient("http://127.0.0.1:" + server.getAddress().getPort(), token);
    }

    @Test
    void sendsInlineKeyboardAsReplyMarkup() {
        boolean sent = client("token").sendMessage(1L, "x", List.of(List.of(new InlineButton("a", "X:1"))));

        assertThat(sent).isTrue();
        assertThat(path.get()).isEqualTo("/bottoken/sendMessage");
        assertThat(body.get()).contains("inline_keyboard").contains("\"callback_data\":\"X:1\"");
    }

    @Test
    void answersCallbackQueryById() {
        boolean answered = client("token").answerCallbackQuery("cb1");

        assertThat(answered).isTrue();
        assertThat(path.get()).endsWith("/answerCallbackQuery");
        assertThat(body.get()).contains("\"callback_query_id\":\"cb1\"");
    }

    @Test
    void blankTokenSendsNothing() {
        assertThat(client("").sendMessage(1L, "x", List.of(List.of(new InlineButton("a", "X:1"))))).isFalse();
        assertThat(client("  ").answerCallbackQuery("cb1")).isFalse();
        assertThat(requests.get()).isZero();
    }

    @Test
    void rejectsCallbackDataOver64Bytes() {
        String data = "ف".repeat(33); // 66 بایت در UTF-8
        assertThatThrownBy(() -> client("token").sendMessage(1L, "x", List.of(List.of(new InlineButton("a", data)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }
}
