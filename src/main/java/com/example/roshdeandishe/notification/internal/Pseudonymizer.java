package com.example.roshdeandishe.notification.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * بی‌نام‌سازی متن آزاد کاربر پیش از ارسال به مدل (مشخصات «Pseudonymizer» در openspec/changes/bale-smart-bot.md):
 * نام کاربر و فرزندان با برچسب جایگزین می‌شود و رشته‌ی رقم‌های بلند (تلفن، کد ملی) به {@code [عدد]} تبدیل می‌شود.
 *
 * <p>متن و نام‌ها هر دو با یک تابع نرمال می‌شوند (ي و ى به ی، ك به ک، حذفِ کشیده و نویسه‌های نامرئیِ
 * جهت/اتصال، نیم‌فاصله و هر فاصله‌ی یونیکدی مثل NBSP به فاصله، فاصله‌های پیاپی به یکی، trim) و
 * خروجی همان متن نرمال‌شده است. نام کامل پیش از نام کوچک و بلندترین تطابق پیش از کوتاه‌تر جایگزین می‌شود
 * («علیرضا» پیش از «علی»). هر تکه‌ی نامی که فقط به یک نام تعلق دارد (نام کوچک یا خانوادگی) با برچسب همان نام، و هر تکه‌ی مشترک میان چند نام (مثل نام
 * خانوادگی خواهر و برادر)، به {@code [نام]} تبدیل می‌شود تا هویت یک فرزند لو نرود. تطابق فقط در مرز واژه است:
 * نویسه‌ی قبل و بعد نباید حرف ({@link Character#isLetter(int)}) باشد.
 */
final class Pseudonymizer {

    /** نام واقعی و برچسبی که جایگزینش می‌شود. */
    record NamedLabel(String name, String label) {
    }

    private static final String GENERIC_NAME = "[نام]";
    private static final String NUMBER = "[عدد]";

    /**
     * ≥۵ رقم (لاتین، فارسی یا عربی-هندی) با حداکثر دو جداکننده‌ی فاصله، «-»، «.»، «/»، «(» یا «)» میان رقم‌ها و
     * «(» اختیاری در آغاز، مثل «(0912) 345 6789».
     */
    private static final Pattern DIGIT_RUN = Pattern.compile(
            "\\(?[0-9\u06F0-\u06F9\u0660-\u0669](?:[ \\-./()]{0,2}[0-9\u06F0-\u06F9\u0660-\u0669]){4,}");

    /** هر فاصله‌ی یونیکدی (NBSP، U+202F، فاصله‌ی باریک، فاصله‌ی CJK و …) و فاصله‌ی صفرعرض. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\u200B]+", Pattern.UNICODE_CHARACTER_CLASS);

    /** کشیده و نویسه‌های نامرئیِ اتصال/جهت که درونِ واژه می‌نشینند؛ حذف می‌شوند، نه فاصله. */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\u0640\u200D\u200E\u200F\uFEFF\u202A-\u202E\u2066-\u2069]");

    private Pseudonymizer() {
    }

    static String pseudonymize(String text, List<NamedLabel> names) {
        String normalised = normalise(text);
        String replaced = replaceNames(normalised, names == null ? List.of() : names);
        return DIGIT_RUN.matcher(replaced).replaceAll(NUMBER);
    }

    private static String normalise(String s) {
        if (s == null) {
            return "";
        }
        String letters = s.replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')
                .replace('\u200C', ' ');
        String visible = INVISIBLE.matcher(letters).replaceAll("");
        return WHITESPACE.matcher(visible).replaceAll(" ").trim();
    }

    private static String replaceNames(String text, List<NamedLabel> names) {
        // الگو ← برچسب؛ نام کامل‌ها بلندترین‌اول، سپس تکه‌ها بلندترین‌اول
        Map<String, String> full = new LinkedHashMap<>();
        Map<String, Integer> tokenCount = new HashMap<>();
        Map<String, String> tokenLabel = new HashMap<>();
        for (NamedLabel n : names) {
            String name = normalise(n.name());
            if (name.isEmpty()) {
                continue;
            }
            full.putIfAbsent(name, n.label());
            String[] tokens = WHITESPACE.split(name);
            for (String t : new LinkedHashSet<>(List.of(tokens))) {
                tokenCount.merge(t, 1, Integer::sum);
            }
            for (String t : new LinkedHashSet<>(List.of(tokens))) {
                tokenLabel.putIfAbsent(t, n.label());
            }
        }
        List<Map.Entry<String, String>> patterns = new ArrayList<>();
        patterns.addAll(sortedLongestFirst(full));
        Map<String, String> tokens = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : tokenCount.entrySet()) {
            if (e.getValue() > 1) {
                tokens.put(e.getKey(), GENERIC_NAME);
            }
        }
        for (Map.Entry<String, String> e : tokenLabel.entrySet()) {
            if (tokenCount.get(e.getKey()) == 1 && !full.containsKey(e.getKey())) {
                tokens.put(e.getKey(), e.getValue());
            }
        }
        patterns.addAll(sortedLongestFirst(tokens));
        if (patterns.isEmpty()) {
            return text;
        }

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int matched = 0;
            String label = null;
            if (startsAtBoundary(text, i)) {
                for (Map.Entry<String, String> p : patterns) {
                    String key = p.getKey();
                    if (text.regionMatches(true, i, key, 0, key.length()) && endsAtBoundary(text, i + key.length())) {
                        matched = key.length();
                        label = p.getValue();
                        break;
                    }
                }
            }
            if (label != null) {
                out.append(label);
                i += matched;
            } else {
                int cp = text.codePointAt(i);
                out.appendCodePoint(cp);
                i += Character.charCount(cp);
            }
        }
        return out.toString();
    }

    private static List<Map.Entry<String, String>> sortedLongestFirst(Map<String, String> m) {
        List<Map.Entry<String, String>> list = new ArrayList<>(m.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        return list;
    }

    private static boolean startsAtBoundary(String text, int index) {
        return index == 0 || !Character.isLetter(text.codePointBefore(index));
    }

    private static boolean endsAtBoundary(String text, int end) {
        return end >= text.length() || !Character.isLetter(text.codePointAt(end));
    }
}
