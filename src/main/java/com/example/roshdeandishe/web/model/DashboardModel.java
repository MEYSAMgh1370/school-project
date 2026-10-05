package com.example.roshdeandishe.web.model;

import java.util.List;

public record DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                             List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards,
                             List<TuitionRow> tuition) {

    /** بدونِ بخشِ شهریه‌ی مدیر (کاربرِ غیرمدیر). */
    public DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                          List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards) {
        this(fullName, email, phoneNumber, memberships, schoolEntries, parent, wards, List.of());
    }
}
