package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * طبقه‌بندیِ نتیجه‌ی ارسال بله (openspec/changes/event-reminders.md) روی یک سرور HTTP واقعیِ محلی، بدون Docker:
 * 200 = SENT؛ 4xx غیر از 429 و توکن خالی = UNDELIVERABLE (تلاش دوباره فایده ندارد)؛ 429، 5xx و قطع اتصال =
 * TEMPORARY_FAILURE (تلاش دوباره منطقی است).
 */
class BaleClientDeliverTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{}".getBytes();
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

    private BaleClient client(String token) {
        return new BaleClient("http://127.0.0.1:" + server.getAddress().getPort(), token);
    }

    private DeliveryResult deliverWith(int httpStatus) {
        status.set(httpStatus);
        return client("token").deliver(1L, "سلام");
    }

    @Test
    void aSuccessfulSendIsSent() {
        assertThat(deliverWith(200)).isEqualTo(DeliveryResult.SENT);
        assertThat(client("token").sendMessage(1L, "سلام")).isTrue();
    }

    @Test
    void clientErrorsExceptTooManyRequestsAreFinal() {
        for (int code : new int[]{400, 401, 403, 404}) {
            assertThat(deliverWith(code)).as("HTTP " + code).isEqualTo(DeliveryResult.UNDELIVERABLE);
        }
        assertThat(client("token").sendMessage(1L, "سلام")).isFalse();
    }

    @Test
    void tooManyRequestsAndServerErrorsAreTemporary() {
        for (int code : new int[]{429, 500, 502, 503}) {
            assertThat(deliverWith(code)).as("HTTP " + code).isEqualTo(DeliveryResult.TEMPORARY_FAILURE);
        }
    }

    @Test
    void anUnreachableServerIsTemporary() {
        BaleClient client = client("token");
        server.stop(0);

        assertThat(client.deliver(1L, "سلام")).isEqualTo(DeliveryResult.TEMPORARY_FAILURE);
    }

    @Test
    void aMissingTokenIsFinalAndNeverCallsTheServer() {
        assertThat(client("").deliver(1L, "سلام")).isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(client("   ").deliver(1L, "سلام")).isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(requests.get()).isZero();
    }
}
