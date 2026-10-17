package com.example.roshdeandishe.notification.internal;

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

/** کیبورد پاسخ پایدار (منو) روی یک سرور HTTP واقعیِ محلی، بدون Docker. */
class BaleClientMenuTest {

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
    void sendsPersistentReplyKeyboard() {
        boolean sent = client("token").sendMenu(1L, "x", List.of(List.of("حضور امروز")));

        assertThat(sent).isTrue();
        assertThat(path.get()).isEqualTo("/bottoken/sendMessage");
        assertThat(body.get())
                .contains("\"keyboard\":[[\"حضور امروز\"]]")
                .contains("\"resize_keyboard\":true")
                .contains("\"one_time_keyboard\":false");
    }

    @Test
    void menuWithBlankTokenSendsNothing() {
        assertThat(client("").sendMenu(1L, "x", List.of(List.of("حضور امروز")))).isFalse();
        assertThat(requests.get()).isZero();
    }
}
