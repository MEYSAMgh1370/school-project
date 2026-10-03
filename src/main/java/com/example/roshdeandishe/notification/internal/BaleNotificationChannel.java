package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.bale.BaleFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * کانال Bale: فقط یک wrapper نازک روی {@link BaleFacade}. فقط وقتی bean ساخته
 * می‌شود که {@code roshdeandishe.bale.bot-token} غیرخالی باشد؛ مقدار پیش‌فرض
 * در application.yml رشته‌ی خالی است، پس {@code @ConditionalOnProperty} ساده
 * (که «وجود property» را کافی می‌داند) اینجا اشتباه است. خودِ BaleClient
 * همچنان بدون‌شرط است چون وب‌هوک ورودی در هر حال به آن نیاز دارد.
 */
@Component
@ConditionalOnExpression("!'${roshdeandishe.bale.bot-token:}'.isBlank()")
class BaleNotificationChannel implements NotificationChannel {

    private final BaleFacade baleFacade;

    BaleNotificationChannel(BaleFacade baleFacade) {
        this.baleFacade = baleFacade;
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        return baleFacade.sendMessageToUser(userId, text);
    }
}
