package com.example.roshdeandishe.curriculum;

import java.util.List;

/**
 * ایمپورت پلتفرمیِ بسته‌ی مشاوره (openspec/changes/moshavere-content-import.md §6، فاز ۱): سرفصل‌ها و درس‌ها
 * در یک تراکنش upsert می‌شوند. curriculum به academic وابسته است (نه برعکس) چون درس‌ها را از طریق AcademicFacade
 * می‌نویسد.
 */
public interface MoshavereImportFacade {

    /**
     * ابتدا کل بسته اعتبارسنجی می‌شود (InvalidMoshavereBundle با اولین مشکل، بدون هیچ نوشتنی)، سپس همه‌ی سرفصل‌ها و
     * بعد همه‌ی درس‌ها در یک تراکنش upsert می‌شوند. {@code dryRun}: همان کار انجام می‌شود ولی تراکنش rollback می‌شود
     * و شمارش‌ها همچنان گزارش می‌شوند. «ساخته‌شده» یعنی ردیف پیش از upsert وجود نداشت.
     */
    ImportSummary importBundle(Bundle bundle, boolean dryRun);

    record Bundle(Integer format, String source, List<TopicEntry> topics, List<CourseEntry> courses) {
    }

    record TopicEntry(String sourceCode, String parentSourceCode, String name, Integer sortOrder) {
    }

    record CourseEntry(Integer gradeLevel, String name, String track, String topicSourceCode) {
    }

    record ImportSummary(int topicsCreated, int topicsUpdated, int coursesCreated, int coursesUpdated,
                         int coursesUnlinked, boolean dryRun) {
    }
}
