package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * باکس سؤالات (openspec/changes/question-box.md): جدول‌های schema questionbox و ماژول/مجوزِ iam وجود دارند.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuestionBoxModuleBootTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void questionboxTablesExist() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'questionbox'", String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "questions", "question_images", "headers", "sheets", "sheet_sections", "sheet_items");
    }

    @Test
    void moduleAndPermissionAreSeededAndGranted() {
        Integer modules = jdbc.queryForObject(
                "select count(*) from iam.modules where code = 'QUESTION_BOX' and scope = 'SCHOOL'", Integer.class);
        assertThat(modules).isEqualTo(1);
        List<String> roles = jdbc.queryForList("""
                select r.code from iam.role_permissions rp
                join iam.roles r on r.id = rp.role_id
                join iam.permissions p on p.id = rp.permission_id
                where p.code = 'QUESTION_BOX_MANAGE' and r.school_id is null
                """, String.class);
        assertThat(roles).containsExactlyInAnyOrder("TEACHER", "SCHOOL_ADMIN");
    }
}
