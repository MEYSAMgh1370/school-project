package com.example.roshdeandishe.notification.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی AttendanceExceptionHandler: {@code @Order(0)} و نام Bean صریح از طریق
 * {@code @Component("...")}. نگاشت خطا به ProblemDetail هم در همین کلاس است (متنِ فارسی).
 */
@RestControllerAdvice
@Component("notificationExceptionHandler")
@Order(0)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    public ProblemDetail handleNotificationException(NotificationException ex) {
        return resolve(ex.error());
    }

    static ProblemDetail resolve(NotificationError error) {
        return switch (error) {
            case NotificationError.BotTokenInvalid e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "bale-bot-token-invalid",
                    "توکن بات نامعتبر است",
                    "توکن واردشده توسط بله پذیرفته نشد. توکن را از BotFather دوباره کپی کنید."
            );
            case NotificationError.BotAlreadyRegistered e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "bale-bot-already-registered",
                    "این بات قبلاً ثبت شده است",
                    "این بات برای مدرسه‌ی دیگری ثبت شده است. برای هر مدرسه باید بات جداگانه بسازید."
            );
            case NotificationError.BaleUnavailable e -> ProblemDetailFactory.problem(
                    HttpStatus.SERVICE_UNAVAILABLE, "bale-unavailable",
                    "بله در دسترس نیست",
                    "ارتباط با بله برقرار نشد. کمی بعد دوباره تلاش کنید."
            );
            case NotificationError.BotEncryptionUnavailable e -> ProblemDetailFactory.problem(
                    HttpStatus.SERVICE_UNAVAILABLE, "bale-bot-encryption-unavailable",
                    "ثبت بات در دسترس نیست",
                    "کلید رمزنگاری توکن در این محیط تنظیم نشده است؛ با پشتیبانی تماس بگیرید."
            );
            case NotificationError.TooManyAttempts e -> ProblemDetailFactory.problem(
                    HttpStatus.TOO_MANY_REQUESTS, "bale-bot-too-many-attempts",
                    "تلاش‌های بیش از حد",
                    "در هر ساعت حداکثر ۱۰ بار می‌توانید بات را ثبت کنید. بعداً دوباره تلاش کنید."
            );
            case NotificationError.SchoolBotNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "school-bot-not-found",
                    "باتی ثبت نشده است",
                    "برای مدرسه‌ی " + e.schoolId() + " باتی ثبت نشده است."
            );
        };
    }
}
