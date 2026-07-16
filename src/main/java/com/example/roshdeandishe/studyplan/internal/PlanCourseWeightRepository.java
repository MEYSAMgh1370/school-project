package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlanCourseWeightRepository extends JpaRepository<PlanCourseWeightEntity, Long> {

    /**
     * ترتیب پیمایش الگوریتم پیشنهاد: priority صعودی سپس id - جایگزین قطعیِ
     * «ترتیب آرایه‌ی JSON» موshavere (رجوع به StudyPlanFacade).
     */
    List<PlanCourseWeightEntity> findByPlanIdOrderByPriorityAscIdAsc(Long planId);
}
