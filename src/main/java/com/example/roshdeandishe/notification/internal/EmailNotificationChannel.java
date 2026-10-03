package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * کانال ایمیل: userId را با {@link IamFacade#findUser} به نشانی ایمیل تبدیل
 * می‌کند و با همان {@link JavaMailSender}ای می‌فرستد که iam برای ایمیل دعوت
 * هم استفاده می‌کند (زیرساخت تازه‌ای لازم نیست). Best-effort: کاربر/ایمیل
 * پیدا نشود یا SMTP شکست بخورد، false برمی‌گردد و Exception بالا نمی‌رود.
 * <p>
 * شرطِ معکوسِ {@link BaleNotificationChannel}: این کانال فقط وقتی ساخته می‌شود
 * که bale تنظیم <em>نشده</em> باشد. دلیل: معیار پذیرش W5 ایمیل را برای
 * «deployment بدون تنظیمات bale» تعریف کرده، و فن‌اوتِ هم‌زمان به هر دو کانال
 * یعنی هر مدرسه‌ی ایرانیِ موجود از این پس به‌ازای هر فاکتور یک ایمیل اضافه هم
 * می‌گرفت - تغییرِ رفتارِ تولیدی که کسی نخواسته است.
 * <p>
 * این انتخاب per-deployment است، نه per-tenant. انتخاب واقعیِ per-tenant (طبق
 * {@code decisions/2026-10-03-multi-market-not-migration.md}) به سیم‌کشی زبانِ
 * مدرسه نیاز دارد که هنوز انجام نشده؛ تا آن موقع این شرط رفتار فعلی را
 * دست‌نخورده نگه می‌دارد.
 */
@Component
@ConditionalOnExpression("'${roshdeandishe.bale.bot-token:}'.isBlank()")
class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private static final String SUBJECT = "اطلاع‌رسانی رشد اندیشه";

    private final IamFacade iamFacade;
    private final JavaMailSender mailSender;
    private final String from;

    EmailNotificationChannel(
            IamFacade iamFacade,
            JavaMailSender mailSender,
            @Value("${roshdeandishe.mail.from}") String from
    ) {
        this.iamFacade = iamFacade;
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        Optional<String> email = iamFacade.findUser(new UserId(userId))
                .map(IamFacade.UserProfile::email)
                .filter(address -> !address.isBlank());
        if (email.isEmpty()) {
            log.warn("اطلاع‌رسانی ایمیلی رد شد: برای userId={} ایمیلی پیدا نشد", userId);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.get());
        message.setSubject(SUBJECT);
        message.setText(text);

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("ارسال ایمیل اطلاع‌رسانی به userId={} شکست خورد.", userId, e);
            return false;
        }
    }
}
