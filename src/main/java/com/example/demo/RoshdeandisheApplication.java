package com.example.roshdeandishe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * نقطه ورود اصلی اپلیکیشن.
 * <p>
 * انوتیشن {@link Modulithic} به Spring Modulith اعلام می‌کند که این پروژه
 * یک Modular Monolith است و مرز هر ماژول از روی پکیج‌های زیرِ
 * {@code com.example.roshdeandishe} (مثل iam, academic, finance) تشخیص داده می‌شود.
 * <p>
 * displayName صرفاً برای خوانایی بهتر در داشبورد Observability/Actuator استفاده می‌شود.
 */
@SpringBootApplication
@Modulithic(displayName = "Roshdeandishe School Management System")
public class RoshdeandisheApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoshdeandisheApplication.class, args);
    }
}
