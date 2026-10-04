package com.example.roshdeandishe.iam.dto.response;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;

public record MyMembershipResponse(Long membershipId, Long schoolId, String roleCode, boolean active) {
    public static MyMembershipResponse from(MembershipInfo info) {
        return new MyMembershipResponse(
                info.membershipId(), info.schoolId(), info.roleCode(), info.active()
        );
    }
}
