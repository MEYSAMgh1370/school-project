package com.example.roshdeandishe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * نشست وب در پستگرس (openspec/changes/jdbc-backed-web-sessions.md) روی دیتابیس واقعی.
 * <p>
 * اولین تست مهم‌ترین است و از یک اشتباه واقعی در همین تغییر می‌آید: وابستگیِ
 * {@code org.springframework.session:spring-session-jdbc} تنها، نشست را به دیتابیس منتقل
 * نمی‌کند. در Spring Boot 4 هر autoconfiguration artifact خودش را دارد و بدون
 * {@code spring-boot-session-jdbc} کتابخانه در classpath هست ولی هیچ‌وقت wire نمی‌شود -
 * لاگین ۲۰۰ می‌دهد، هیچ خطایی لاگ نمی‌شود، و نشست بی‌صدا در حافظه می‌ماند. تنها نشانه‌اش
 * خالی بودن جدول است. پس نوعِ bean صریحاً assert می‌شود، نه فقط رفتارش.
 * <p>
 * تست دوم همان ادعای اصلی این تغییر را می‌سنجد: نشست و attribute هایش واقعاً در
 * iam.spring_session می‌نشینند و با شناسه از دیتابیس بازخوانی می‌شوند - یعنی پروسه‌ی بعدی
 * (بعد از دیپلوی یا ری‌استارت) همان نشست را پیدا می‌کند.
 * <p>
 * هر تست ردیف خودش را پاک می‌کند، چون دیتابیس بین تست‌ها ریست نمی‌شود.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WebSessionPersistenceFlowIntegrationTest {

    private static final String ATTRIBUTE = "roshdeandishe.test.attribute";
    private static final String VALUE = "پایدار";

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private String createdSessionId;

    @AfterEach
    void removeCreatedSession() {
        if (createdSessionId != null) {
            sessionRepository.deleteById(createdSessionId);
            createdSessionId = null;
        }
    }

    /**
     * نوعِ واقعیِ نشستِ Spring Session بیرونِ پکیج خودش قابل نام‌بردن نیست (JdbcSession
     * عمومی نیست)، پس createSession و save باید داخل یک متد generic صدا زده شوند تا
     * پارامتر نوع یک‌بار capture شود و هر دو فراخوانی روی همان نوع انجام شوند.
     */
    private <S extends Session> String saveNewSession(SessionRepository<S> repository) {
        S created = repository.createSession();
        created.setAttribute(ATTRIBUTE, VALUE);
        repository.save(created);
        return created.getId();
    }

    @Test
    void sessionRepositoryIsTheJdbcOneAndNotAnInMemoryFallback() {
        assertThat(sessionRepository)
                .as("نشست باید از مسیر JDBC برود؛ هر نوع دیگری یعنی autoconfiguration وصل نشده "
                        + "و نشست‌ها بی‌صدا در حافظه می‌مانند")
                .isInstanceOf(JdbcIndexedSessionRepository.class);
    }

    @Test
    void sessionAndItsAttributesAreReadableBackFromPostgres() {
        createdSessionId = saveNewSession(sessionRepository);

        Integer sessionRows = jdbc.queryForObject(
                "select count(*) from iam.spring_session where session_id = ?",
                Integer.class, createdSessionId);
        assertThat(sessionRows)
                .as("نشست ذخیره‌شده باید یک ردیف در iam.spring_session داشته باشد")
                .isEqualTo(1);

        Integer attributeRows = jdbc.queryForObject("""
                select count(*)
                from iam.spring_session_attributes a
                join iam.spring_session s on s.primary_id = a.session_primary_id
                where s.session_id = ? and a.attribute_name = ?
                """, Integer.class, createdSessionId, ATTRIBUTE);
        assertThat(attributeRows)
                .as("attribute نشست هم باید در iam.spring_session_attributes نوشته شود")
                .isEqualTo(1);

        // بازخوانی از دیتابیس: همان کاری که پروسه‌ی بعد از ری‌استارت انجام می‌دهد.
        Session reloaded = sessionRepository.findById(createdSessionId);
        assertThat(reloaded).isNotNull();
        assertThat((String) reloaded.getAttribute(ATTRIBUTE)).isEqualTo(VALUE);
    }

    @Test
    void flywayOwnsTheSessionTables() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from iam.flyway_schema_history where version = '20' and success",
                Integer.class);
        assertThat(applied)
                .as("جدول‌های نشست باید از مسیر مهاجرت V20 ساخته شوند، نه توسط Spring Session "
                        + "(spring.session.jdbc.initialize-schema روی never است)")
                .isEqualTo(1);
    }
}
