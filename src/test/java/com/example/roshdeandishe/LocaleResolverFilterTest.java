package com.example.roshdeandishe;

import com.example.roshdeandishe.platform.LocaleContext;
import com.example.roshdeandishe.platform.config.LocaleResolverFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تست واحدِ {@link LocaleResolverFilter} و {@link LocaleContext}: بدون
 * Spring context و بدون دیتابیس. زبانِ دیده‌شده توسط زنجیره (در حین Request)
 * ضبط می‌شود، چون پس از پایان Request نگهدارنده عمداً پاک است.
 */
class LocaleResolverFilterTest {

    private final LocaleResolverFilter filter = new LocaleResolverFilter();

    /**
     * Request را با هدر داده‌شده (یا بدون هدر اگر null) اجرا می‌کند و زبانی را
     * برمی‌گرداند که داخل زنجیره در {@link LocaleContext} دیده شده است.
     */
    private Locale resolvedFor(String acceptLanguage) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/app.css");
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Locale> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(LocaleContext.getOrNull());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        return seen.get();
    }

    @Test
    void noHeaderResolvesToPersian() throws Exception {
        assertThat(resolvedFor(null)).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void englishHeaderResolvesToEnglish() throws Exception {
        assertThat(resolvedFor("en")).isEqualTo(Locale.forLanguageTag("en"));
    }

    @Test
    void regionalEnglishWithQualityResolvesToEnglish() throws Exception {
        assertThat(resolvedFor("en-US,en;q=0.9")).isEqualTo(Locale.forLanguageTag("en"));
    }

    @Test
    void persianHeaderResolvesToPersian() throws Exception {
        assertThat(resolvedFor("fa-IR,fa;q=0.9,en;q=0.5")).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void unsupportedLanguageFallsBackToPersian() throws Exception {
        assertThat(resolvedFor("de")).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void malformedHeaderFallsBackToPersianWithoutError() throws Exception {
        assertThat(resolvedFor("!!!;;q=oops,,;")).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void blankHeaderFallsBackToPersian() throws Exception {
        assertThat(resolvedFor("   ")).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void contextIsClearedAfterRequestAndUnsetReadNeverThrows() throws Exception {
        resolvedFor("en");

        assertThat(LocaleContext.isSet()).isFalse();
        assertThat(LocaleContext.getOrNull()).isNull();
        assertThat(LocaleContext.getOrDefault()).isEqualTo(Locale.forLanguageTag("fa"));
    }

    @Test
    void contextIsClearedEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader("Accept-Language", "en");
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception expected) {
            // نتیجه‌ی مورد انتظار - فقط پاک‌سازی finally مهم است
        }

        assertThat(LocaleContext.isSet()).isFalse();
    }
}
