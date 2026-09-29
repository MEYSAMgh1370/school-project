package com.example.roshdeandishe.iam.dto;

import java.util.List;

public record CreateRoleRequest(String code, String displayName, List<String> permissionCodes) {
}
