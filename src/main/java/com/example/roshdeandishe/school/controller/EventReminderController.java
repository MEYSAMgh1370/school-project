package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.dto.request.SetReminderRolesRequest;
import com.example.roshdeandishe.school.dto.response.ReminderRolesResponse;
import com.example.roshdeandishe.school.dto.response.UpcomingEventsResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * یادآوری رویدادهای برنامه‌ی سالانه (openspec/changes/event-reminders.md): مدیر (YEARLY_PLAN_MANAGE) نقش‌های
 * دریافت‌کننده را انتخاب می‌کند؛ عضوِ فعالِ دریافت‌کننده رویدادهای نزدیکِ مدرسه را برای جعبه‌ی داشبورد می‌خواند.
 * زمانِ یادآوری روی خودِ رویداد است (YearlyPlanController).
 */
@RestController
public class EventReminderController {

    /** وقتی مدرسه منطقه‌ی زمانیِ معتبر ندارد. */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Tehran");
    private static final String PLATFORM_ROLE_PREFIX = "PLATFORM_";

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public EventReminderController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/admin/schools/{schoolId}/yearly-plan/reminder-roles")
    @RequiresPermission("YEARLY_PLAN_MANAGE")
    public ReminderRolesResponse reminderRoles(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return rolesResponse(schoolId);
    }

    @PutMapping("/api/admin/schools/{schoolId}/yearly-plan/reminder-roles")
    @RequiresPermission("YEARLY_PLAN_MANAGE")
    public ReminderRolesResponse setReminderRoles(
            @PathVariable Long schoolId, @RequestBody SetReminderRolesRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.roleCodes() == null || request.roleCodes().isEmpty()) {
            throw new SchoolException(new SchoolError.InvalidSetting(
                    "دست‌کم یک نقش برای دریافت یادآوری باید انتخاب شود."));
        }
        Set<String> allowed = new HashSet<>();
        roleOptions(schoolId).forEach(role -> allowed.add(role.code()));
        Set<String> wanted = new HashSet<>();
        for (String code : request.roleCodes()) {
            if (code == null || !allowed.contains(code)) {
                throw new SchoolException(new SchoolError.InvalidSetting(
                        "نقش «" + code + "» جزو نقش‌های این مدرسه نیست."));
            }
            wanted.add(code);
        }
        schoolFacade.setReminderRoleCodes(new SchoolId(schoolId), wanted);
        return rolesResponse(schoolId);
    }

    @GetMapping("/api/me/schools/{schoolId}/upcoming-events")
    public UpcomingEventsResponse upcomingEvents(@PathVariable Long schoolId) {
        // فقط عضو فعالِ همین مدرسه (هر نقشی)؛ مثل CalendarController.myCalendar
        IamFacade.UserId callerId = iamFacade.currentUserId();
        boolean activeMember = iamFacade.findMembershipsByUser(callerId).stream()
                .anyMatch(membership -> schoolId.equals(membership.schoolId()) && membership.active());
        if (!activeMember) {
            throw new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId));
        }
        SchoolId school = new SchoolId(schoolId);
        Optional<Set<String>> selected = schoolFacade.reminderRoleCodes(school);
        if (selected.isPresent()) {
            Set<String> mine = iamFacade.findActiveRoleCodes(callerId, schoolId);
            if (mine.stream().noneMatch(selected.get()::contains)) {
                return new UpcomingEventsResponse(List.of());
            }
        }
        LocalDate today = LocalDate.now(zoneOf(school));
        List<UpcomingEventsResponse.Event> events = schoolFacade.dueReminderEvents(school, today).stream()
                .map(event -> toResponse(event, today))
                .toList();
        return new UpcomingEventsResponse(events);
    }

    private ZoneId zoneOf(SchoolId school) {
        String timezone = schoolFacade.findSchool(school).map(SchoolInfo::timezone).orElse(null);
        if (timezone == null || timezone.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            return FALLBACK_ZONE;
        }
    }

    private static UpcomingEventsResponse.Event toResponse(ReminderEvent event, LocalDate today) {
        return new UpcomingEventsResponse.Event(
                event.id(), event.day(), event.title(), (int) ChronoUnit.DAYS.between(today, event.day()));
    }

    private List<ReminderRolesResponse.Role> roleOptions(Long schoolId) {
        // همان فهرستِ ویرایشگر نقش (listRoles: سیستمی + سفارشیِ همین مدرسه)؛ نقش پلتفرم هرگز
        return iamFacade.listRoles(schoolId).stream()
                .filter(role -> role.code() != null && !role.code().startsWith(PLATFORM_ROLE_PREFIX))
                .map(role -> new ReminderRolesResponse.Role(role.code(), role.displayName()))
                .toList();
    }

    private ReminderRolesResponse rolesResponse(Long schoolId) {
        List<String> selected = schoolFacade.reminderRoleCodes(new SchoolId(schoolId))
                .map(codes -> List.copyOf(new TreeSet<>(codes)))
                .orElse(null);
        return new ReminderRolesResponse(roleOptions(schoolId), selected);
    }
}
