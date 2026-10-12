package com.example.roshdeandishe.curriculum.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    /**
     * برای تغییر وضعیت: قفل نوشتن روی ردیف، تا دو مدیرِ همزمان گذارِ OPEN→REVIEWED یا REVIEWED→FIXED/CLOSED را
     * پشت‌سرهم ببینند (دومی وضعیتِ تازه را می‌خواند و رد می‌شود) نه هر دو از یک وضعیتِ کهنه رد شوند.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QuestionReportEntity r where r.id = :id")
    Optional<QuestionReportEntity> findByIdForUpdate(@Param("id") Long id);
}
