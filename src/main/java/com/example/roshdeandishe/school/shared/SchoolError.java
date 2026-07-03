package com.example.roshdeandishe.school.shared;

public sealed interface SchoolError {

    record SchoolNotFound(Long schoolId) implements SchoolError {
    }
}
