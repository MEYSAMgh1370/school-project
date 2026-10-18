package com.example.roshdeandishe.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PseudonymizerTest {

    private static final List<Pseudonymizer.NamedLabel> KIDS = List.of(
            new Pseudonymizer.NamedLabel("علی رضایی", "[فرزند_۱]"),
            new Pseudonymizer.NamedLabel("علیرضا رضایی", "[فرزند_۲]"));

    @Test
    void replacesFullNameAndFirstName() {
        assertThat(Pseudonymizer.pseudonymize("نمره‌های علی رضایی و علیرضا", KIDS))
                .isEqualTo("نمره های [فرزند_۱] و [فرزند_۲]");
    }

    @Test
    void prefersLongestAndRespectsWordBoundaries() {
        assertThat(Pseudonymizer.pseudonymize("علیرضا امروز رفت؟", KIDS)).isEqualTo("[فرزند_۲] امروز رفت؟");
        assertThat(Pseudonymizer.pseudonymize("از رضایت شما ممنون", KIDS)).isEqualTo("از رضایت شما ممنون");
    }

    @Test
    void sharedTokenBecomesGenericName() {
        assertThat(Pseudonymizer.pseudonymize("رضایی امتحان داره؟", KIDS)).isEqualTo("[نام] امتحان داره؟");
    }

    @Test
    void uniqueFamilyNameIsReplaced() {
        List<Pseudonymizer.NamedLabel> names = List.of(
                new Pseudonymizer.NamedLabel("علی رضایی", "[فرزند_۱]"),
                new Pseudonymizer.NamedLabel("مریم احمدی", "[فرزند_۲]"));
        assertThat(Pseudonymizer.pseudonymize("احمدی امتحان داره؟", names)).isEqualTo("[فرزند_۲] امتحان داره؟");
    }

    @Test
    void normalisesArabicLettersAndZwnj() {
        assertThat(Pseudonymizer.pseudonymize("نمره هاي علي", KIDS)).isEqualTo("نمره های [فرزند_۱]");
        assertThat(Pseudonymizer.pseudonymize("نمره‌ها", List.of())).isEqualTo("نمره ها");
    }

    @Test
    void replacesLongDigitRunsInAnyScript() {
        assertThat(Pseudonymizer.pseudonymize("شماره ۰۹۱۲ ۳۴۵ ۶۷۸۹ و 1234567890 و ٠٩١٢٣٤٥٦٧٨٩", List.of()))
                .isEqualTo("شماره [عدد] و [عدد] و [عدد]");
        assertThat(Pseudonymizer.pseudonymize("کلاس ۱۰۲", List.of())).isEqualTo("کلاس ۱۰۲");
    }
    @Test
    void nbspInStoredNameStillMatches() {
        List<Pseudonymizer.NamedLabel> names = List.of(new Pseudonymizer.NamedLabel("علی\u00A0رضایی", "[فرزند_۱]"));
        assertThat(Pseudonymizer.pseudonymize("نمره‌های علی", names)).isEqualTo("نمره های [فرزند_۱]");
        assertThat(Pseudonymizer.pseudonymize("نمره‌های علی\u202Fرضایی", names)).isEqualTo("نمره های [فرزند_۱]");
        assertThat(Pseudonymizer.pseudonymize("نمره\u3000علی\u2009رضایی", KIDS)).isEqualTo("نمره [فرزند_۱]");
    }

    @Test
    void kashidaInsideNameIsRemoved() {
        assertThat(Pseudonymizer.pseudonymize("نمره‌های علـــی", KIDS)).isEqualTo("نمره های [فرزند_۱]");
        List<Pseudonymizer.NamedLabel> names = List.of(new Pseudonymizer.NamedLabel("مریـم احمدی", "[فرزند_۱]"));
        assertThat(Pseudonymizer.pseudonymize("مریم امروز رفت؟", names)).isEqualTo("[فرزند_۱] امروز رفت؟");
    }

    @Test
    void alefMaksuraIsNormalised() {
        assertThat(Pseudonymizer.pseudonymize("على", KIDS)).isEqualTo("[فرزند_۱]");
        List<Pseudonymizer.NamedLabel> names = List.of(new Pseudonymizer.NamedLabel("مصطفى رضایی", "[فرزند_۱]"));
        assertThat(Pseudonymizer.pseudonymize("مصطفی امروز رفت؟", names)).isEqualTo("[فرزند_۱] امروز رفت؟");
    }

    @Test
    void invisibleFormatCharsInsideWordDoNotBreakMatch() {
        for (String c : List.of("\u200D", "\u200E", "\u200F", "\uFEFF", "\u202A", "\u202B", "\u202C",
                "\u202D", "\u202E", "\u2066", "\u2067", "\u2068", "\u2069")) {
            assertThat(Pseudonymizer.pseudonymize("نمره‌های عل" + c + "ی", KIDS))
                    .as("U+%04X", (int) c.charAt(0))
                    .isEqualTo("نمره های [فرزند_۱]");
        }
        List<Pseudonymizer.NamedLabel> names = List.of(new Pseudonymizer.NamedLabel("\u2067علی\u200Dرضا\u2069", "[فرزند_۱]"));
        assertThat(Pseudonymizer.pseudonymize("علیرضا", names)).isEqualTo("[فرزند_۱]");
    }

    @Test
    void phoneSeparatorsDotSlashAndParenthesesAreDigitRuns() {
        assertThat(Pseudonymizer.pseudonymize("شماره 0912.345.6789", List.of())).isEqualTo("شماره [عدد]");
        assertThat(Pseudonymizer.pseudonymize("شماره (0912) 345 6789", List.of())).isEqualTo("شماره [عدد]");
        assertThat(Pseudonymizer.pseudonymize("شماره 0912/345/6789", List.of())).isEqualTo("شماره [عدد]");
        assertThat(Pseudonymizer.pseudonymize("شماره ۰۹۱۲-۳۴۵-۶۷۸۹", List.of())).isEqualTo("شماره [عدد]");
        assertThat(Pseudonymizer.pseudonymize("کلاس ۱۰۲ و ساعت ۸", List.of())).isEqualTo("کلاس ۱۰۲ و ساعت ۸");
    }
}
