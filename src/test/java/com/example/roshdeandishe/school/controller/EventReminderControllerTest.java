package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.RoleInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.dto.request.SetReminderRolesRequest;
import com.example.roshdeandishe.school.dto.response.ReminderRolesResponse;
import com.example.roshdeandishe.school.dto.response.UpcomingEventsResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * قاعده‌ی گیرنده‌ی جعبه‌ی «رویدادهای نزدیک» و اعتبارسنجیِ نقش‌های یادآوری
 * (openspec/changes/event-reminders.md) بدون Spring و Docker. مجوز YEARLY_PLAN_MANAGE با aspect اعمال می‌شود
 * و در تست flow پوشش داده می‌شود.
 */
class EventReminderControllerTest {

    private static final Long SCHOOL = 5L;
    private static final UserId USER = new UserId(11L);

    private SchoolFacade schoolFacade;
    private IamFacade iamFacade;
    private EventReminderController controller;

    @BeforeEach
    void setUp() {
        schoolFacade = mock(SchoolFacade.class);
        iamFacade = mock(IamFacade.class);
        controller = new EventReminderController(schoolFacade, iamFacade);
        when(iamFacade.currentUserId()).thenReturn(USER);
        // بدون رکورد مدرسه → منطقه‌ی زمانیِ پیش‌فرض (Asia/Tehran)
        when(schoolFacade.findSchool(any())).thenReturn(Optional.empty());
        TenantContext.set(SCHOOL);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Tehran"));
    }

    private void member(boolean active, Long schoolId) {
        when(iamFacade.findMembershipsByUser(USER)).thenReturn(List.of(
                new MembershipInfo(1L, USER, "u@x", "کاربر", schoolId, "STUDENT", active, true)));
    }

    private void oneDueEvent() {
        when(schoolFacade.dueReminderEvents(any(), any())).thenReturn(
                List.of(new ReminderEvent(70L, today().plusDays(2), "اردو", 3)));
    }

    // ---------------------------------------------------------------- upcoming-events

    @Test
    void withoutASelectionEveryActiveMemberSeesTheDueEventsWithDaysLeft() {
        member(true, SCHOOL);
        when(schoolFacade.reminderRoleCodes(new SchoolId(SCHOOL))).thenReturn(Optional.empty());
        oneDueEvent();

        UpcomingEventsResponse response = controller.upcomingEvents(SCHOOL);

        assertThat(response.events()).hasSize(1);
        UpcomingEventsResponse.Event event = response.events().get(0);
        assertThat(event.id()).isEqualTo(70L);
        assertThat(event.title()).isEqualTo("اردو");
        assertThat(event.daysLeft()).isEqualTo(2);
        // نقش‌ها اصلاً لازم نیست خوانده شوند
        verify(iamFacade, never()).findActiveRoleCodes(any(), any());
    }

    @Test
    void aSelectedRoleSeesTheEventsAndAnotherRoleGetsAnEmptyListWithoutQueryingThem() {
        member(true, SCHOOL);
        when(schoolFacade.reminderRoleCodes(new SchoolId(SCHOOL))).thenReturn(Optional.of(Set.of("PARENT")));
        oneDueEvent();

        when(iamFacade.findActiveRoleCodes(USER, SCHOOL)).thenReturn(Set.of("PARENT", "TEACHER"));
        assertThat(controller.upcomingEvents(SCHOOL).events()).hasSize(1);

        when(iamFacade.findActiveRoleCodes(USER, SCHOOL)).thenReturn(Set.of("TEACHER"));
        assertThat(controller.upcomingEvents(SCHOOL).events()).isEmpty();
        // برای غیرگیرنده فهرست رویدادها پرس‌وجو نمی‌شود (یک‌بار برای گیرنده)
        verify(schoolFacade, org.mockito.Mockito.times(1)).dueReminderEvents(any(), any());
    }

    @Test
    void aNonMemberOrAnInactiveMemberOrAMemberOfAnotherSchoolIsRejected() {
        member(false, SCHOOL);
        assertThatThrownBy(() -> controller.upcomingEvents(SCHOOL)).isInstanceOf(IamException.class);

        member(true, 99L);
        assertThatThrownBy(() -> controller.upcomingEvents(SCHOOL)).isInstanceOf(IamException.class);

        when(iamFacade.findMembershipsByUser(USER)).thenReturn(List.of());
        assertThatThrownBy(() -> controller.upcomingEvents(SCHOOL)).isInstanceOf(IamException.class);
        verify(schoolFacade, never()).dueReminderEvents(any(), any());
    }

    // ---------------------------------------------------------------- reminder-roles

    private void schoolRoles() {
        when(iamFacade.listRoles(SCHOOL)).thenReturn(List.of(
                new RoleInfo(1L, "STUDENT", "دانش‌آموز", null, List.of()),
                new RoleInfo(2L, "PARENT", "والد", null, List.of()),
                new RoleInfo(3L, "PLATFORM_ADMIN", "مدیر پلتفرم", null, List.of()),
                new RoleInfo(4L, "COACH", "مربی", SCHOOL, List.of())));
    }

    @Test
    void theRoleListHasTheSchoolsRolesButNeverAPlatformRoleAndNullMeansNothingSelected() {
        schoolRoles();
        when(schoolFacade.reminderRoleCodes(new SchoolId(SCHOOL))).thenReturn(Optional.empty());

        ReminderRolesResponse response = controller.reminderRoles(SCHOOL);

        assertThat(response.roles()).extracting(ReminderRolesResponse.Role::code)
                .containsExactly("STUDENT", "PARENT", "COACH");
        assertThat(response.selected()).isNull();
    }

    @Test
    void aValidSelectionIsSavedAndEchoedSortedIncludingACustomRole() {
        schoolRoles();
        when(schoolFacade.reminderRoleCodes(new SchoolId(SCHOOL))).thenReturn(Optional.of(Set.of("PARENT", "COACH")));

        ReminderRolesResponse response = controller.setReminderRoles(
                SCHOOL, new SetReminderRolesRequest(List.of("PARENT", "COACH")));

        verify(schoolFacade).setReminderRoleCodes(new SchoolId(SCHOOL), Set.of("PARENT", "COACH"));
        assertThat(response.selected()).containsExactly("COACH", "PARENT");
    }

    @Test
    void anUnknownPlatformOrNullCodeAndAnEmptyOrMissingSelectionAreRejected() {
        schoolRoles();
        for (List<String> codes : java.util.Arrays.<List<String>>asList(
                List.of("NOPE"), List.of("PLATFORM_ADMIN"), List.of("PARENT", "NOPE"),
                java.util.Arrays.asList("PARENT", null), List.of(), null)) {
            assertThatThrownBy(() -> controller.setReminderRoles(SCHOOL, new SetReminderRolesRequest(codes)))
                    .isInstanceOfSatisfying(SchoolException.class,
                            e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidSetting.class));
        }
        assertThatThrownBy(() -> controller.setReminderRoles(SCHOOL, null))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidSetting.class));
        verify(schoolFacade, never()).setReminderRoleCodes(any(), any());
    }

    @Test
    void aTenantMismatchIsRefusedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> controller.reminderRoles(SCHOOL + 1)).isInstanceOf(RuntimeException.class);
        verify(schoolFacade, never()).reminderRoleCodes(any());
    }
}
