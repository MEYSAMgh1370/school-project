package com.example.roshdeandishe.web.dto;

public record SchoolEntryRow(String schoolName, String slug) {

    public boolean hasSlug() {
        return slug != null && !slug.isBlank();
    }
}
