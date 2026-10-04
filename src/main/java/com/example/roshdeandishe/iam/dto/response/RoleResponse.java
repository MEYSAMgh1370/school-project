package com.example.roshdeandishe.iam.dto.response;

import com.example.roshdeandishe.iam.IamFacade.RoleInfo;

import java.util.List;

public record RoleResponse(Long id, String code, String displayName, boolean custom, List<String> permissionCodes) {
    public static RoleResponse from(RoleInfo info) {
        return new RoleResponse(info.id(), info.code(), info.displayName(), info.schoolId() != null, info.permissionCodes());
    }
}
