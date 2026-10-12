package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface QuestionReportRepository extends JpaRepository<QuestionReportEntity, Long> {

    /** صف مدرسه: همه‌ی گزارش‌هایی که کاربران این مدرسه ثبت کرده‌اند (فیلتر وضعیت در سرویس). */
    @Query("select r from QuestionReportEntity r where r.schoolId = :schoolId order by r.createdAt desc, r.id desc")
    List<QuestionReportEntity> findForSchool(@Param("schoolId") Long schoolId);

    @Query("select r from QuestionReportEntity r where r.schoolId = :schoolId and r.reporterUserId = :userId "
            + "order by r.createdAt desc, r.id desc")
    List<QuestionReportEntity> findByReporter(@Param("schoolId") Long schoolId, @Param("userId") Long userId);

    /** صف پلتفرم: گزارش‌های سؤال‌های پلتفرمی (school_id تهی) از هر مدرسه (فیلتر وضعیت در سرویس). */
    @Query("select r from QuestionReportEntity r, QuestionEntity q where r.questionId = q.id "
            + "and q.schoolId is null order by r.createdAt desc, r.id desc")
    List<QuestionReportEntity> findOnPlatformQuestions();
}
