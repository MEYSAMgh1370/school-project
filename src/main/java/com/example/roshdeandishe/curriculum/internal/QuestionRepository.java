package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {

    /** سؤال پلتفرمی با این کدِ منبع (کلید upsert ایمپورت). */
    Optional<QuestionEntity> findBySourceCodeAndSchoolIdIsNull(String sourceCode);

    /** سؤال‌های قابل‌مشاهده برای مدرسه: پلتفرمی (school_id null) + خودِ مدرسه. */
    @Query("select q from QuestionEntity q where q.schoolId is null or q.schoolId = :schoolId order by q.id asc")
    List<QuestionEntity> findVisibleToSchool(@Param("schoolId") Long schoolId);

    @Query("select q from QuestionEntity q where (q.schoolId is null or q.schoolId = :schoolId) "
            + "and q.topicId in :topicIds order by q.id asc")
    List<QuestionEntity> findVisibleToSchoolByTopicIds(@Param("schoolId") Long schoolId,
                                                       @Param("topicIds") Collection<Long> topicIds);
}
