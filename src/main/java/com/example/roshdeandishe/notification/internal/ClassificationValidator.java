package com.example.roshdeandishe.notification.internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** خروجیِ مدل را پیش از استفاده اعتبارسنجی می‌کند؛ هر ناهمخوانی یعنی «نامشخص». */
final class ClassificationValidator {
    private static final String NONE = "NONE";

    /** {@code label} ممکن است null باشد؛ آن‌گاه روتر با دکمه می‌پرسد. */
    record Resolved(BotIntent intent, String label) {
    }

    private ClassificationValidator() {
    }

    static Optional<Resolved> validate(ClassificationAnswer a, Set<BotIntent> allowed,
                                       List<String> labels, double minConfidence) {
        if (a == null || a.intent() == null || NONE.equals(a.intent())) {
            return Optional.empty();
        }
        BotIntent intent;
        try {
            intent = BotIntent.valueOf(a.intent());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!allowed.contains(intent) || a.intentConfidence() < minConfidence) {
            return Optional.empty();
        }
        String label = null;
        if (!labels.isEmpty() && a.target() != null && !NONE.equals(a.target())) {
            if (!labels.contains(a.target())) {
                return Optional.empty();
            }
            if (a.targetConfidence() >= minConfidence) {
                label = a.target();
            }
        }
        return Optional.of(new Resolved(intent, label));
    }
}
