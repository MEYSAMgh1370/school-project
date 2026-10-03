package com.example.roshdeandishe;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * پیش‌تر اینجا یک {@code withInitScript("init-schemas.sql")} بود تا هشت schemaی
 * ماژول‌ها قبل از بالا آمدن Hibernate ساخته شوند. حالا Flyway خودش آن‌ها را از
 * روی {@code spring.flyway.schemas} می‌سازد، پس آن اسکریپت حذف شد - و هر اجرای
 * تست عملاً مهاجرت‌ها را روی یک دیتابیس کاملاً خالی راستی‌آزمایی می‌کند، دقیقاً
 * همان مسیری که یک استقرار تازه طی می‌کند.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"));
    }
}
