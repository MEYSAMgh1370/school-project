package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * backfillِ حضور و غیابِ مدرسه‌های موجود (attendance.md تصمیم ۶) بدون Spring و Docker: هر مدرسه‌ای که ردیف
 * ندارد می‌گیرد، دوباره‌اجرا هیچ‌چیز را عوض نمی‌کند، و مدرسه‌ی تازه‌ای که بعداً می‌آید در اجرای بعدی می‌گیرد
 * (به‌ازای هر مدرسه، نه شرطِ «شمارِ ردیف‌ها»).
 */
class AttendanceEntitlementBackfillTest {

    @Test
    void grantsEverySchoolThatLacksItAndIsIdempotentOnRerun() {
        SchoolRepository schools = mock(SchoolRepository.class);
        IamFacade iam = mock(IamFacade.class);
        // «پایگاه‌داده‌ی» iam: مدرسه‌هایی که ردیفِ ATTENDANCE دارند؛ مدرسه‌ی ۱ از قبل دارد
        Set<Long> granted = new HashSet<>(Set.of(1L));
        when(iam.ensureSchoolEntitlement(anyLong(), any(ModuleCode.class)))
                .thenAnswer(invocation -> granted.add(invocation.getArgument(0)));
        when(schools.findAll()).thenReturn(List.of(school(1L), school(2L), school(3L)));
        AttendanceEntitlementBackfill backfill = new AttendanceEntitlementBackfill(schools, iam);

        assertThat(backfill.backfill()).isEqualTo(2);
        assertThat(granted).containsExactlyInAnyOrder(1L, 2L, 3L);

        // اجرای دوم: همه دارند، چیزی عوض نمی‌شود
        assertThat(backfill.backfill()).isZero();
        assertThat(granted).containsExactlyInAnyOrder(1L, 2L, 3L);

        // مدرسه‌ای که بعد از اجرای اول ساخته شده در اجرای بعدی (ریستارت) می‌گیرد، نه هرگز
        when(schools.findAll()).thenReturn(List.of(school(1L), school(2L), school(3L), school(4L)));
        assertThat(backfill.backfill()).isEqualTo(1);
        assertThat(granted).contains(4L);
    }

    @Test
    void asksForTheAttendanceModuleOnly() {
        SchoolRepository schools = mock(SchoolRepository.class);
        IamFacade iam = mock(IamFacade.class);
        when(schools.findAll()).thenReturn(List.of(school(7L)));

        new AttendanceEntitlementBackfill(schools, iam).backfill();

        org.mockito.Mockito.verify(iam).ensureSchoolEntitlement(7L, new ModuleCode("ATTENDANCE"));
        org.mockito.Mockito.verifyNoMoreInteractions(iam);
    }

    private static SchoolEntity school(Long id) {
        SchoolEntity school = new SchoolEntity("مدرسه " + id, "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null);
        school.setId(id);
        return school;
    }
}
