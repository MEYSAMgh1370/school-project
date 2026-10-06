package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * پرچمِ نمایشیِ «مدیر پلتفرم» در مدل ناوبار (بدون Spring و Docker): مدیر پلتفرم معمولاً هیچ عضویت مدرسه‌ای ندارد،
 * پس مدل باید با فهرست مدرسه‌ی خالی هم ساخته شود و پرچم فقط از {@code IamFacade.isPlatformAdmin} بیاید.
 */
class NavbarModelAssemblerTest {

    private static final UserId USER = new UserId(10L);

    private final IamFacade iam = mock(IamFacade.class);
    private final SchoolFacade school = mock(SchoolFacade.class);
    private final NavbarModelAssembler assembler = new NavbarModelAssembler(iam, school);

    private NavbarModel forUserWithoutMemberships(boolean platformAdmin) {
        when(iam.currentUserId()).thenReturn(USER);
        when(iam.findUser(USER)).thenReturn(Optional.of(new UserProfile(USER, "a@example.com", "مدیر پلتفرم", null)));
        when(iam.findMembershipsByUser(USER)).thenReturn(List.of());
        when(iam.isPlatformAdmin(USER)).thenReturn(platformAdmin);
        return assembler.forCurrentUser();
    }

    @Test
    void aPlatformAdminWithoutAnySchoolMembershipGetsTheFlagAndNoSchoolOptions() {
        NavbarModel navbar = forUserWithoutMemberships(true);

        assertThat(navbar.platformAdmin()).isTrue();
        assertThat(navbar.schools()).isEmpty();
        assertThat(navbar.roleNames()).isEmpty();
        assertThat(navbar.weekStartsJson()).isEqualTo("{}");
    }

    @Test
    void anyoneElseDoesNotGetTheFlag() {
        assertThat(forUserWithoutMemberships(false).platformAdmin()).isFalse();
    }
}
