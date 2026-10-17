package com.example.roshdeandishe.notification.controller;

import com.example.roshdeandishe.notification.NotificationFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * دریافت‌کننده‌ی وب‌هوک بله. عمداً بدون {@code @RequiresModule}/
 * {@code @RequiresPermission}: فراخوان‌کننده سرور بله است، نه یک مرورگر
 * با سشن - این دو aspect به SecurityContext/TenantContext یک session
 * نیاز دارند که اینجا اصلاً وجود ندارد (SecurityConfig این مسیر را
 * permitAll کرده و از CSRF مستثنا کرده - رجوع به iam.shared.SecurityConfig).
 * <p>
 * امنیت این endpoint از طریق یک بخش مخفی در خودِ URL تأمین می‌شود
 * (راز مشترک roshdeandishe.bale.webhook-secret یا رازِ ربات یک مدرسه) - همان الگویی که
 * خیلی از ارائه‌دهنده‌های وب‌هوک (Stripe و مشابه) پیشنهاد می‌کنند وقتی امضای
 * رمزنگاری‌شده در دسترس نباشد؛ تشخیص ربات از روی راز (با مقایسه‌ی زمان‌ثابت برای
 * راز مشترک) در NotificationFacade انجام می‌شود.
 */
@RestController
@RequestMapping("/api/bale/webhook")
public class BaleWebhookController {

    private final NotificationFacade notificationFacade;

    public BaleWebhookController(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @PostMapping("/{secret}")
    public ResponseEntity<Void> receiveUpdate(@PathVariable String secret, @RequestBody String rawBody) {
        if (!notificationFacade.processIncomingUpdate(secret, rawBody)) {
            // 404، نه 403: به یک اسکنر خارجی حتی وجود این مسیر را هم لو نمی‌دهیم
            return ResponseEntity.notFound().build();
        }
        // پس از پذیرفتن Update فوراً ۲۰۰ می‌دهیم؛ پردازش ناهمگام است (رجوع به BaleWebhookService)
        return ResponseEntity.ok().build();
    }
}
