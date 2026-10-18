package com.example.roshdeandishe.finance.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentNameMatcherTest {

    @Test
    void firstNameMatchesTheFirstWord() {
        assertThat(StudentNameMatcher.matches("علی رضایی", "علی", "", "")).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", "رضایی", "", "")).isFalse();
    }

    @Test
    void lastNameMatchesTheRemainingWordsAndNormalisesArabicLetters() {
        assertThat(StudentNameMatcher.matches("علی رضایی", "", "رضايي", "")).isTrue();
        assertThat(StudentNameMatcher.matches("علی", "", "رضایی", "")).isFalse();
        assertThat(StudentNameMatcher.matches("علی رضا کریمی", "", "رضا کریمی", "")).isTrue();
        // نام تک‌کلمه‌ای: نام‌خانوادگی تهی است و فقط فیلتر تهی را می‌پذیرد
        assertThat(StudentNameMatcher.matches("علی", "علی", "", "")).isTrue();
    }

    @Test
    void generalQueryMatchesAnywhereInTheWholeName() {
        assertThat(StudentNameMatcher.matches("علی رضایی", "", "", "ضای")).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", "", "", "علی رض")).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", "", "", "کریمی")).isFalse();
    }

    @Test
    void blankOrNullFiltersMatchEverything() {
        assertThat(StudentNameMatcher.matches("علی رضایی", "", "", "")).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", null, null, null)).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", "  ", " ", "   ")).isTrue();
        assertThat(StudentNameMatcher.matches(null, null, null, null)).isTrue();
        assertThat(StudentNameMatcher.matches(null, "علی", null, null)).isFalse();
    }

    @Test
    void allSuppliedFiltersMustMatch() {
        assertThat(StudentNameMatcher.matches("علی رضایی", "علی", "رضایی", "ضا")).isTrue();
        assertThat(StudentNameMatcher.matches("علی رضایی", "علی", "کریمی", "")).isFalse();
        assertThat(StudentNameMatcher.matches("علی رضایی", "علی", "رضایی", "کریمی")).isFalse();
    }

    @Test
    void normalisationHandlesKafZwnjTatweelSpacesAndCase() {
        assertThat(StudentNameMatcher.matches("کریمی", "", "", "كريمي")).isTrue();
        assertThat(StudentNameMatcher.matches("می‌رزایی", "", "", "میرزایی")).isTrue();
        assertThat(StudentNameMatcher.matches("میرزایی", "", "", "می‌رزایی")).isTrue();
        assertThat(StudentNameMatcher.matches("علـی  رضایی", "علی", "رضایی", "")).isTrue();
        assertThat(StudentNameMatcher.matches("  علی    رضایی ", "علی", "رضایی", "")).isTrue();
        assertThat(StudentNameMatcher.matches("Ali Smith", "ali", "SMITH", "li sm")).isTrue();
    }

    @Test
    void normalisedNameIsExposedForSorting() {
        assertThat(StudentNameMatcher.normalise("  كريم‌  علـي ")).isEqualTo("کریم علی");
    }
}
