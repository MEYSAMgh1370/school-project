package com.example.roshdeandishe.academic.dto.request;

public record EnrollStudentRequest(String email, String fullName, Long classId, String track) {
}
