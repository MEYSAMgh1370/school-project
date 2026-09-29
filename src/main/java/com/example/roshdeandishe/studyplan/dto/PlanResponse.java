package com.example.roshdeandishe.studyplan.dto;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;

import java.util.List;

public record PlanResponse(Long id, Long schoolId, Long teacherId, String title, List<CourseWeight> weights) {
}
