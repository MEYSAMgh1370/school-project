package com.example.roshdeandishe.platform.config;

import com.example.roshdeandishe.platform.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * مسیرهای پلتفرم (/api/platform/**، /platform/**) هدر X-School-ID نمی‌خواهند و هرگز TenantContext
 * نمی‌گیرند - حتی با هدر؛ وگرنه PermissionAccessAspect آن‌ها را SchoolContext می‌دید. بقیه‌ی رفتار
 * فیلتر (هدر الزامی روی مسیر مدرسه‌ای، TenantContext با هدر) دست‌نخورده می‌ماند.
 */
class TenantResolverFilterPlatformPathTest {

    private final TenantResolverFilter filter = new TenantResolverFilter();

    @AfterEach
    void clean() {
        TenantContext.clear();
    }

    private record Outcome(int status, boolean chainCalled, Long tenantInsideChain) {
    }

    private Outcome call(String uri, String schoolHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (schoolHeader != null) {
            request.addHeader(TenantResolverFilter.TENANT_HEADER, schoolHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Long> tenant = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> {
            called.set(true);
            tenant.set(TenantContext.getOrNull());
        });
        assertThat(TenantContext.isSet()).as("cleared after the request").isFalse();
        return new Outcome(response.getStatus(), called.get(), tenant.get());
    }

    @Test
    void platformPathsPassWithoutTheHeader() throws Exception {
        for (String uri : new String[]{"/api/platform/academic-years", "/api/platform/holidays/5", "/platform/holidays", "/platform"}) {
            Outcome outcome = call(uri, null);
            assertThat(outcome.chainCalled()).as(uri).isTrue();
            assertThat(outcome.status()).as(uri).isEqualTo(200);
            assertThat(outcome.tenantInsideChain()).as(uri).isNull();
        }
    }

    @Test
    void platformPathsNeverSetTenantContextEvenWhenASchoolHeaderIsSent() throws Exception {
        for (String uri : new String[]{"/api/platform/academic-years", "/platform/holidays"}) {
            for (String header : new String[]{"1", "999", "not-a-number", ""}) {
                Outcome outcome = call(uri, header);
                assertThat(outcome.chainCalled()).as(uri + " [" + header + "]").isTrue();
                assertThat(outcome.tenantInsideChain()).as(uri + " [" + header + "]").isNull();
            }
        }
    }

    @Test
    void theSettingsPageNeedsNoHeaderButItsApiDoes() throws Exception {
        // صفحه‌ی HTML با ناوبری باز می‌شود و هدری نمی‌فرستد؛ fetchهای آن (زیر /api/admin/schools/{id}) هدر دارند
        Outcome page = call("/settings", null);
        assertThat(page.chainCalled()).isTrue();
        assertThat(page.status()).isEqualTo(200);

        Outcome api = call("/api/admin/schools/1/settings", null);
        assertThat(api.chainCalled()).isFalse();
        assertThat(api.status()).isEqualTo(400);
    }

    @Test
    void aLookalikePrefixIsNotAPlatformPath() throws Exception {
        Outcome outcome = call("/api/platformish/x", null);

        assertThat(outcome.chainCalled()).isFalse();
        assertThat(outcome.status()).isEqualTo(400);
    }

    @Test
    void schoolPathsStillRequireTheHeaderAndSetTheTenant() throws Exception {
        Outcome missing = call("/api/admin/schools/1/calendar", null);
        assertThat(missing.chainCalled()).isFalse();
        assertThat(missing.status()).isEqualTo(400);

        Outcome withHeader = call("/api/admin/schools/1/calendar", "1");
        assertThat(withHeader.chainCalled()).isTrue();
        assertThat(withHeader.tenantInsideChain()).isEqualTo(1L);
    }
}
