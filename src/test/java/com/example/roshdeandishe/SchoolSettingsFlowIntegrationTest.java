package com.example.roshdeandishe;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.BotSettings;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تنظیماتِ مدرسه (school_settings / bot_roles): ساختِ تنبلِ ردیف از ستون‌های قدیمیِ schools،
 * پیش‌فرض‌های ربات، محدودیتِ نقش‌ها، نوشتنِ دوگانه و امنیتِ خواندنِ هم‌زمانِ اول.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchoolSettingsFlowIntegrationTest {

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** مدرسه‌ی تازه بدون ردیفِ تنظیمات؛ ستون‌های قدیمیِ schools با مقدارهای داده‌شده پر می‌شوند. */
    private SchoolId schoolWithoutSettingsRow(boolean installments, boolean autoPromotion) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        SchoolId id = schoolFacade.createSchool("Settings " + unique, "Asia/Tehran", "fa", SchoolLevel.ELEMENTARY,
                "st-" + unique);
        jdbcTemplate.update("delete from school.bot_roles where school_id = ?", id.value());
        jdbcTemplate.update("delete from school.school_settings where school_id = ?", id.value());
        jdbcTemplate.update("update school.schools set installments_enabled = ?, auto_promotion = ? where id = ?",
                installments, autoPromotion, id.value());
        return id;
    }

    @Test
    void migrationCopiesExistingValues() {
        SchoolId id = schoolWithoutSettingsRow(true, true);

        assertThat(schoolFacade.isInstallmentsEnabled(id)).isTrue();
        assertThat(schoolFacade.isAutoPromotion(id)).isTrue();
    }

    @Test
    void newSchoolDefaults() {
        SchoolId id = schoolWithoutSettingsRow(false, false);

        assertThat(schoolFacade.getBotSettings(id)).isEqualTo(new BotSettings(true, Set.of()));
        assertThat(schoolFacade.isBotAllowed(id, Set.of("PARENT"))).isTrue();
        assertThat(schoolFacade.isInstallmentsEnabled(id)).isFalse();
        assertThat(schoolFacade.isAutoPromotion(id)).isFalse();
    }

    @Test
    void botRolesRestrictAccess() {
        SchoolId id = schoolWithoutSettingsRow(false, false);

        schoolFacade.setBotSettings(id, true, Set.of("PARENT"));
        assertThat(schoolFacade.getBotSettings(id)).isEqualTo(new BotSettings(true, Set.of("PARENT")));
        assertThat(schoolFacade.isBotAllowed(id, Set.of("PARENT"))).isTrue();
        assertThat(schoolFacade.isBotAllowed(id, Set.of("TEACHER"))).isFalse();

        schoolFacade.setBotSettings(id, true, Set.of("TEACHER"));
        assertThat(schoolFacade.isBotAllowed(id, Set.of("PARENT"))).isFalse();
        assertThat(schoolFacade.isBotAllowed(id, Set.of("TEACHER"))).isTrue();

        schoolFacade.setBotSettings(id, false, Set.of());
        assertThat(schoolFacade.isBotAllowed(id, Set.of("PARENT"))).isFalse();
        assertThat(schoolFacade.isBotAllowed(id, Set.of("TEACHER"))).isFalse();
    }

    @Test
    void dualWrite() {
        SchoolId id = schoolWithoutSettingsRow(false, false);

        schoolFacade.setInstallmentsEnabled(id, true);
        schoolFacade.setAutoPromotion(id, true);

        assertThat(schoolFacade.isInstallmentsEnabled(id)).isTrue();
        assertThat(schoolFacade.isAutoPromotion(id)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select installments_enabled from school.schools where id = ?", Boolean.class, id.value())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select auto_promotion from school.schools where id = ?", Boolean.class, id.value())).isTrue();
    }

    @Test
    void concurrentFirstReadCreatesOneRow() throws Exception {
        SchoolId id = schoolWithoutSettingsRow(false, false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<BotSettings> a = pool.submit(() -> {
                start.await();
                return schoolFacade.getBotSettings(id);
            });
            Future<BotSettings> b = pool.submit(() -> {
                start.await();
                return schoolFacade.getBotSettings(id);
            });
            start.countDown();
            assertThat(a.get()).isEqualTo(new BotSettings(true, Set.of()));
            assertThat(b.get()).isEqualTo(new BotSettings(true, Set.of()));
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from school.school_settings where school_id = ?", Integer.class, id.value()))
                .isEqualTo(1);
    }
}
