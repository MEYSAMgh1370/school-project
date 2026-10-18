package com.example.roshdeandishe.notification.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * توکن بات در آدرس هر درخواست است ({@code /bot<token>/...}) و پیام exceptionهای شبکه‌ی Spring آدرس کامل را دارد؛
 * پس هیچ لاگ شکستِ ارسالی نباید خود exception یا پیامش را بنویسد.
 */
@ExtendWith(OutputCaptureExtension.class)
class BaleClientTokenLoggingTest {

    private static final String TOKEN = "SECRET-TOKEN-4242";

    private static BaleClient clientToUnreachableServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.stop(0);
        return new BaleClient("http://127.0.0.1:" + port, TOKEN);
    }

    @Test
    void failedSendsNeverLogTheToken(CapturedOutput output) throws IOException {
        BaleClient client = clientToUnreachableServer();

        client.deliver(1L, "سلام");
        client.sendContactRequest(1L, "سلام");
        client.sendMenu(1L, "سلام", List.of(List.of("راهنما")));
        client.sendMessage(1L, "سلام", List.of(List.of(new BaleClient.InlineButton("الف", "a:1"))));
        client.answerCallbackQuery("cb1");

        assertThat(output.getAll()).contains("شکست خورد").doesNotContain(TOKEN);
    }
}
