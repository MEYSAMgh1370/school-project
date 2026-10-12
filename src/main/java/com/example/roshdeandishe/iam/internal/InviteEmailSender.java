package com.example.roshdeandishe.iam.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * ارسال ایمیل دعوت (لینک فعال‌سازی حساب) به کاربری که هنوز رمز عبور ندارد.
 * <p>
 * ارسال best-effort است: اگر SMTP در دسترس نباشد، صدور توکن نباید شکست
 * بخورد - ادمین همچنان توکن را در پاسخ JSON می‌گیرد و می‌تواند لینک را
 * دستی تحویل دهد (همان جریان قبل از وجود ایمیل). به همین دلیل این کلاس
 * false برمی‌گرداند و لاگ می‌کند، نه اینکه exception را بالا بدهد.
 * <p>
 * عمداً در iam/internal است، نه platform - فقط iam ایمیل می‌فرستد و
 * ساختن یک abstraction عمومی برای نیاز یک ماژول، خلاف YAGNI این پروژه است.
 */
@Component
class InviteEmailSender {

    private static final Logger log = LoggerFactory.getLogger(InviteEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String activationBaseUrl;

    InviteEmailSender(
            JavaMailSender mailSender,
            @Value("${roshdeandishe.mail.from}") String from,
            @Value("${roshdeandishe.mail.activation-base-url}") String activationBaseUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.activationBaseUrl = activationBaseUrl;
    }

    /**
     * @return true اگر ایمیل تحویل SMTP شد؛ false اگر ارسال شکست خورد
     * (توکن همچنان معتبر است و ادمین باید لینک را دستی برساند)
     */
    boolean sendActivationEmail(String recipientEmail, String fullName, String token, String invitationSummary) {
        String activationLink = activationBaseUrl + "/activate?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("فعال‌سازی حساب کاربری رشد اندیشه");
        message.setText("""
                %s عزیز، سلام

                %sبرای شما حسابی در سامانه‌ی رشد اندیشه ساخته شده است. برای \
                تعیین رمز عبور و فعال‌سازی حساب، روی لینک زیر بزنید:

                %s

                این لینک تا ۷ روز معتبر است و فقط یک بار قابل استفاده است. اگر منقضی شد، از مدیر مدرسه \
                بخواهید دعوت‌نامه‌ی تازه‌ای بفرستد.
                اگر این ایمیل را اشتباهی دریافت کرده‌اید، آن را نادیده بگیرید.
                """.formatted(fullName, invitationLine(invitationSummary), activationLink));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("ارسال ایمیل فعال‌سازی به {} شکست خورد؛ ادمین باید لینک را دستی تحویل دهد.",
                    recipientEmail, e);
            return false;
        }
    }

    /** «شما به‌عنوان معلم در دبستان … دعوت شده‌اید.» + یک سطر خالی؛ بدونِ خلاصه چیزی اضافه نمی‌شود. */
    private static String invitationLine(String invitationSummary) {
        if (invitationSummary == null || invitationSummary.isBlank()) {
            return "";
        }
        return "شما به‌عنوان " + invitationSummary + " دعوت شده‌اید.\n\n";
    }

    /**
     * اطلاعِ «شما به یک مدرسه اضافه شدید» برای کاربری که از قبل حساب فعال دارد (دعوت‌نامه‌ی فعال‌سازی برایش
     * بی‌معناست). همان قراردادِ best-effortِ {@link #sendActivationEmail}.
     */
    boolean sendAddedToSchoolEmail(String recipientEmail, String fullName, String invitationSummary) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("شما به یک مدرسه در رشد اندیشه اضافه شدید");
        message.setText("""
                %s عزیز، سلام

                شما به‌عنوان %s اضافه شدید. برای دیدن مدرسه با همان حساب قبلی‌تان وارد شوید:

                %s/login
                """.formatted(fullName, invitationSummary, activationBaseUrl));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("ارسال ایمیل «اضافه شدید» به {} شکست خورد.", recipientEmail, e);
            return false;
        }
    }
}
