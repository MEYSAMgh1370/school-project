package com.example.roshdeandishe.web.model;

public record SchoolEntryModel(Long schoolId, String schoolName, boolean authenticated) {

    public boolean found() {
        return schoolId != null;
    }
}
