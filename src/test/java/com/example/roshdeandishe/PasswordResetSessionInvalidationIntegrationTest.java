package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بعد از تغییرِ موفقِ رمز، نشست‌های وبِ آن کاربر در iam.spring_session بسته می‌شوند
 * (password-reset-by-email.md). مثل WebSessionPersistenceFlowIntegrationTest، پیکربندیِ خودکارِ Spring
 * Session که در تست‌های دیگر کنار گذاشته شده اینجا روشن می‌شود تا مخزنِ JDBC واقعی وجود داشته باشد.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@Import(TestcontainersConfiguration.class)
class PasswordResetSessionInvalidationIntegrationTest {

    @Autowired
    private IamFacade iamFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.session.SessionRepository<? extends Session> sessionRepository;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aSuccessfulResetClosesTheUsersWebSessionsAndLeavesOthersAlone() throws Exception {
        String email = "reset-sessions-" + System.nanoTime() + "@example.test";
        String other = "reset-sessions-other-" + System.nanoTime() + "@example.test";
        iamFacade.registerUser(email, "کاربر آزمایشی", "password123");

        FindByIndexNameSessionRepository repository = (FindByIndexNameSessionRepository) sessionRepository;
        String mine = saveSession(repository, email);
        String theirs = saveSession(repository, other);
        assertThat(repository.findByPrincipalName(email)).hasSize(1);

        String token = "known-test-token-" + System.nanoTime();
        jdbcTemplate.update("""
                insert into iam.password_reset_tokens (user_id, token_hash, created_at, expires_at)
                values ((select id from iam.users where email = ?), ?, now(), now() + interval '1 hour')
                """, email, sha256Hex(token));

        iamFacade.confirmPasswordReset(token, "brandNewPassw0rd");

        assertThat(repository.findByPrincipalName(email)).isEmpty();
        assertThat(repository.findByPrincipalName(other)).containsKey(theirs);
        repository.deleteById(theirs);
        assertThat(mine).isNotBlank();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String saveSession(FindByIndexNameSessionRepository repository, String principal) {
        Session session = (Session) repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
        repository.save(session);
        return session.getId();
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
