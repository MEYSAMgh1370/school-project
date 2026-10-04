package com.example.roshdeandishe.iam.dto.response;

import com.example.roshdeandishe.iam.IamFacade.PermissionInfo;

public record PermissionResponse(Long id, String code, String displayName) {
    public static PermissionResponse from(PermissionInfo info) {
        return new PermissionResponse(info.id(), info.code(), info.displayName());
    }
}
