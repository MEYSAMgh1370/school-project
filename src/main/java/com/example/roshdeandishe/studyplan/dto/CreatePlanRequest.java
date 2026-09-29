package com.example.roshdeandishe.studyplan.dto;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;

import java.util.List;

public record CreatePlanRequest(String title, List<CourseWeight> weights) {
}
