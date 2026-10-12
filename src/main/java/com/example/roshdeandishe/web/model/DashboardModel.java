package com.example.roshdeandishe.web.model;

import java.util.List;

public record DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                             List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                             List<TuitionRow> tuition, List<AttendanceTodayRow> attendanceToday,
                             List<TeacherClassesRow> teacherClasses, String rolesSummary,
                             List<OwnSummaryRow> ownSummaries,
                             List<SetupChecklistRow> setupChecklists) {

    /** بدونِ چک‌لیستِ راه‌اندازی (کاربرِ غیرمدیر / تست‌های قدیمی). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                          List<TuitionRow> tuition, List<AttendanceTodayRow> attendanceToday,
                          List<TeacherClassesRow> teacherClasses, String rolesSummary,
                          List<OwnSummaryRow> ownSummaries) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, tuition, attendanceToday,
                teacherClasses, rolesSummary, ownSummaries, List.of());
    }

    /** بدونِ «خلاصهٔ من» دانش‌آموز (کاربرِ غیردانش‌آموز / تست‌های قدیمی). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                          List<TuitionRow> tuition, List<AttendanceTodayRow> attendanceToday,
                          List<TeacherClassesRow> teacherClasses, String rolesSummary) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, tuition, attendanceToday,
                teacherClasses, rolesSummary, List.of(), List.of());
    }

    /** بدونِ «کلاس‌های من» و خلاصه‌ی نقش‌ها (کاربرِ غیرمعلم / تست‌های قدیمی). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                          List<TuitionRow> tuition, List<AttendanceTodayRow> attendanceToday) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, tuition, attendanceToday,
                List.of(), "");
    }

    /** بدونِ بخش‌های حضور و غیابِ امروز (کاربرِ غیرمدیر یا مدرسه‌ی بدونِ ماژول حضور و غیاب). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                          List<TuitionRow> tuition) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, tuition, List.of());
    }

    /** بدونِ بخشِ شهریه‌ی مدیر (کاربرِ غیرمدیر). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, List.of(), List.of());
    }

    /**
     * مدرسه‌ای که چک‌لیستش هنوز کامل نشده؛ تا آن موقع کارت‌های خالیِ «شهریه» و «حضور و غیاب» همان مدرسه
     * نشان داده نمی‌شوند (کارتی که داده دارد همیشه می‌ماند).
     */
    public boolean setupIncomplete(Long schoolId) {
        return setupChecklists.stream().anyMatch(c -> c.schoolId().equals(schoolId) && !c.complete());
    }
}
