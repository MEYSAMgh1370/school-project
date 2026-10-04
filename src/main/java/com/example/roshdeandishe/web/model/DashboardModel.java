package com.example.roshdeandishe.web.model;

import java.util.List;

public record DashboardModel(String fullName, String email, String phoneNumber, List<MembershipRow> memberships,
                             List<SchoolEntryRow> schoolEntries, boolean parent, List<WardRow> wards) {
}
