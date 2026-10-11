package com.example.roshdeandishe.curriculum.dto.response;

public record MoshavereImportResponse(int topicsCreated, int topicsUpdated, int coursesCreated, int coursesUpdated,
                                      int coursesUnlinked, boolean dryRun) {
}
