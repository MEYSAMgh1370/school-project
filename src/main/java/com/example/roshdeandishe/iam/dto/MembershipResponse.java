package com.example.roshdeandishe.iam.dto;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;

public record MembershipResponse(
        Long membershipId, Long userId, String email, String fullName,
        Long schoolId, String roleCode, boolean active, boolean hasPassword
) {
    public static MembershipResponse from(Long membershipId, MembershipInfo info) {
        return new MembershipResponse(
                membershipId, info.userId().value(), info.email(), info.fullName(),
                info.schoolId(), info.roleCode(), info.active(), info.hasPassword()
        );
    }
}
