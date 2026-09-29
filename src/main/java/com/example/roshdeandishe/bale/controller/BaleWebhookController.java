package com.example.roshdeandishe.bale.controller;

import com.example.roshdeandishe.bale.BaleFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * دریافت‌کننده‌ی وب‌هوک بله. عمداً بدون {@code @RequiresModule}/
 * {@code @RequiresPermission}: فراخوان‌کننده سرور بله است، نه یک مرورگر
 * با سشن - این دو aspect به SecurityContext/TenantContext یک session
 * نیاز دارند که اینجا اصلاً وجود ندارد (SecurityConfig این مسیر را
 * permitAll کرده و از CSRF مستثنا کرده - رجوع به iam.shared.SecurityConfig).
 * <p>
 * امنیت این endpoint از طریق یک بخش مخفی در خودِ URL تأمین می‌شود
 * (roshdeandishe.bale.webhook-secret) - همان الگویی که خیلی از ارائه‌دهنده‌های
 * وب‌هوک (Stripe و مشابه) پیشنهاد می‌کنند وقتی امضای رمزنگاری‌شده در دسترس
 * نباشد؛ مقایسه با {@link MessageDigest#isEqual} انجام می‌شود تا در برابر
 * timing attack مقاوم باشد.
 */
@RestController
@RequestMapping("/api/bale/webhook")
public class BaleWebhookController {

    private final BaleFacade baleFacade;
    private final String webhookSecret;

    public BaleWebhookController(
            BaleFacade baleFacade,
            @Value("${roshdeandishe.bale.webhook-secret}") String webhookSecret
    ) {
        this.baleFacade = baleFacade;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/{secret}")
    public ResponseEntity<Void> receiveUpdate(@PathVariable String secret, @RequestBody String rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank() || !constantTimeEquals(secret, webhookSecret)) {
            // 404، نه 403: به یک اسکنر خارجی حتی وجود این مسیر را هم لو نمی‌دهیم
            return ResponseEntity.notFound().build();
        }

        baleFacade.processIncomingUpdate(rawBody);
        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
