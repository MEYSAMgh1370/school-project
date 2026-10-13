package com.example.roshdeandishe.web.model;

import java.util.List;

/**
 * چک‌لیستِ راه‌اندازیِ یک مدرسه برای مدیرِ آن (school-admin-onboarding.md، بسته‌ی ۲). گام‌ها در
 * {@link SetupChecklistAssembler} ساخته می‌شوند؛ این رکورد فقط شمارش را از روی همان فهرست می‌دهد.
 */
public record SetupChecklistRow(Long schoolId, String schoolName, List<SetupStep> steps) {

    public int total() {
        return steps.size();
    }

    public int doneCount() {
        return (int) steps.stream().filter(SetupStep::done).count();
    }

    public boolean complete() {
        return doneCount() == total();
    }

    /** عددِ صحیحِ ۰ تا ۱۰۰ برای نوارِ پیشرفت. */
    public int percent() {
        return total() == 0 ? 100 : doneCount() * 100 / total();
    }
}
