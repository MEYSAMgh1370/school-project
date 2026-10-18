package com.example.roshdeandishe.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassificationValidatorTest {
    private static final Set<BotIntent> ALLOWED = EnumSet.of(BotIntent.GRADES, BotIntent.EVENTS);
    private static final List<String> LABELS = List.of("[فرزند_۱]", "[فرزند_۲]", "NONE");

    private static ClassificationAnswer a(String intent, double ic, String target, double tc) {
        return new ClassificationAnswer(intent, ic, target, tc, "m");
    }

    @Test
    void acceptsAllowedConfidentIntentAndLabel() {
        assertThat(ClassificationValidator.validate(a("GRADES", .9, "[فرزند_۲]", .8), ALLOWED, LABELS, .7))
                .contains(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۲]"));
    }

    @Test
    void roleDisallowedIntentIsRejected() {
        assertThat(ClassificationValidator.validate(a("INVOICES", .99, "NONE", 1), ALLOWED, LABELS, .7)).isEmpty();
    }

    @Test
    void unknownNoneOrLowConfidenceIntentIsRejected() {
        assertThat(ClassificationValidator.validate(a("DROP TABLE", 1, null, 0), ALLOWED, LABELS, .7)).isEmpty();
        assertThat(ClassificationValidator.validate(a("NONE", 1, null, 0), ALLOWED, LABELS, .7)).isEmpty();
        assertThat(ClassificationValidator.validate(a("GRADES", .69, "NONE", 1), ALLOWED, LABELS, .7)).isEmpty();
    }

    @Test
    void labelNotOfferedIsRejected() {
        assertThat(ClassificationValidator.validate(a("GRADES", .9, "[فرزند_۹]", .99), ALLOWED, LABELS, .7)).isEmpty();
    }

    @Test
    void uncertainOrNoneTargetLeavesLabelEmpty() {
        assertThat(ClassificationValidator.validate(a("GRADES", .9, "[فرزند_۱]", .5), ALLOWED, LABELS, .7))
                .contains(new ClassificationValidator.Resolved(BotIntent.GRADES, null));
        assertThat(ClassificationValidator.validate(a("GRADES", .9, "NONE", .9), ALLOWED, LABELS, .7))
                .contains(new ClassificationValidator.Resolved(BotIntent.GRADES, null));
    }
}
