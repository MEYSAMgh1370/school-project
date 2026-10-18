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
}
