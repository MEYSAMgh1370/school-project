package com.example.roshdeandishe.iam.dto.request;

import java.util.List;

public record CreateRoleRequest(String code, String displayName, List<String> permissionCodes) {
}
