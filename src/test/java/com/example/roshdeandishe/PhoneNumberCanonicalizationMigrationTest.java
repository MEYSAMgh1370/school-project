package com.example.roshdeandishe;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PhoneNumberCanonicalizationMigrationTest {

    private static final String[] SCHEMAS = {
            "iam", "school", "academic", "finance", "bale", "curriculum", "studyplan", "exam", "attendance", "questionbox"
    };

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"));

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = connection()) {
            for (String schema : SCHEMAS) {
                connection.createStatement().execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    @Test
    void migrationCanonicalizesExistingDigitsAndBoundaryWhitespace() throws Exception {
        flyway("2").migrate();
        execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values
                    ('persian@example.test', 'Persian', 'ACTIVE', ' ۰۹۱۲۳۴۵۶۷۸۹ '),
                    ('arabic@example.test', 'Arabic', 'ACTIVE', '٠٩٣٥١٢٣٤٥٦٧'),
                    ('tabbed@example.test', 'Tabbed', 'ACTIVE', E'\\t۰۹۱۱۲۳۴۵۶۷۸\\t'),
                    ('nbsp@example.test', 'NBSP', 'ACTIVE', U&'\\00A0٠٩٤٤١٢٣٤٥٦٧\\202F')
                """);

        flyway(null).migrate();

        assertThat(phoneNumbers()).containsExactlyInAnyOrder(
                "09123456789", "09351234567", "09112345678", "09441234567");
        assertThatThrownBy(() -> execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values ('late-legacy@example.test', 'Legacy', 'ACTIVE', '۰۹۹۹۱۲۳۴۵۶۷')
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_users_phone_number_canonical");
        assertThatThrownBy(() -> execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values ('late-whitespace@example.test', 'Whitespace', 'ACTIVE', U&'\\00A009991234567\\00A0')
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_users_phone_number_canonical");
    }

    @Test
    void migrationRefusesToGuessOwnershipForCanonicalCollisions() throws Exception {
        flyway("2").migrate();
        execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values
                    ('ascii-owner@example.test', 'ASCII owner', 'ACTIVE', '09125550123'),
                    ('persian-owner@example.test', 'Persian owner', 'ACTIVE', '۰۹۱۲۵۵۵۰۱۲۳')
                """);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("visually equivalent phone numbers belong to multiple users");

        assertThat(phoneNumbers()).containsExactlyInAnyOrder("09125550123", "۰۹۱۲۵۵۵۰۱۲۳");
    }

    @Test
    void boundaryWhitespaceMigrationRefusesToGuessOwnershipForPostV3Collisions() throws Exception {
        flyway("3").migrate();
        execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values
                    ('ascii-owner@example.test', 'ASCII owner', 'ACTIVE', '09129991234'),
                    ('nbsp-owner@example.test', 'NBSP owner', 'ACTIVE', U&'\\00A009129991234\\202F')
                """);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("visually equivalent phone numbers belong to multiple users");

        assertThat(phoneNumbers()).containsExactlyInAnyOrder("09129991234", "\u00a009129991234\u202f");
    }

    @Test
    void v32RewritesIranianMobileFormsAndLeavesOtherNumbersAlone() throws Exception {
        flyway("31").migrate();
        execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values
                    ('a@example.test', 'A', 'ACTIVE', '989335551287'),
                    ('b@example.test', 'B', 'ACTIVE', '+98 912 555 0123'),
                    ('c@example.test', 'C', 'ACTIVE', '00989121112233'),
                    ('d@example.test', 'D', 'ACTIVE', '9351234567'),
                    ('e@example.test', 'E', 'ACTIVE', '021-88776655'),
                    ('f@example.test', 'F', 'ACTIVE', '+1 (555) 123-4567')
                """);

        flyway(null).migrate();

        assertThat(phoneNumbers()).containsExactly(
                "09335551287", "09125550123", "09121112233", "09351234567",
                "021-88776655", "+1 (555) 123-4567");
        assertThatThrownBy(() -> execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values ('late@example.test', 'Late', 'ACTIVE', '+989339998877')
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_users_phone_number_iranian_mobile_canonical");
    }

    @Test
    void v32RefusesToGuessOwnershipForMobileCollisions() throws Exception {
        flyway("31").migrate();
        execute("""
                insert into iam.users (email, full_name, status, phone_number)
                values
                    ('national@example.test', 'National', 'ACTIVE', '09335551287'),
                    ('bale@example.test', 'Bale', 'ACTIVE', '989335551287')
                """);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("the same Iranian mobile number belongs to multiple users");

        assertThat(phoneNumbers()).containsExactly("989335551287", "09335551287");
    }

    @Test
    void v33DropsHibernateNamedUniqueConstraintsOnBaleLinks() throws Exception {
        flyway("32").migrate();
        // دیتابیسی که با ddl-auto=update ساخته شده، constraintها را با نامِ تولیدیِ Hibernate دارد نه نامِ V1
        execute("""
                alter table bale.bale_links rename constraint bale_links_chat_id_key to uk_hibernate_chat;
                alter table bale.bale_links rename constraint bale_links_user_id_key to uk_hibernate_user;
                insert into bale.bale_links (chat_id, user_id, linked_at) values (11, 22, now())
                """);

        flyway(null).migrate();

        execute("""
                insert into bale.school_bots (school_id, bot_user_id, bot_username, token_ciphertext, token_key_version,
                                              webhook_secret_hash, status, created_at, updated_at)
                values (1, 100, 'b', 'c', 1, 'h', 'ACTIVE', now(), now());
                insert into bale.bale_links (bot_id, chat_id, user_id, linked_at)
                select id, 11, 22, now() from bale.school_bots
                """);
        // ربات مشترک همچنان یک پیوند به‌ازای chat_id/user_id دارد
        assertThatThrownBy(() -> execute(
                "insert into bale.bale_links (chat_id, user_id, linked_at) values (11, 33, now())"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(
                "insert into bale.bale_links (chat_id, user_id, linked_at) values (44, 22, now())"))
                .isInstanceOf(SQLException.class);
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(SCHEMAS)
                .defaultSchema("iam")
                .baselineOnMigrate(true)
                .baselineVersion("1");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> phoneNumbers() throws SQLException {
        try (Connection connection = connection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select phone_number from iam.users order by email")) {
            var phoneNumbers = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                phoneNumbers.add(resultSet.getString(1));
            }
            return phoneNumbers;
        }
    }
}
