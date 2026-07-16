package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {

    /**
     * فیلتر اختیاری روی سرفصل/سختی در یک کوئری - پورتِ QuestionFilter
     * (django-filter) در moshavere. پارامتر null یعنی «بدون فیلتر».
     */
    @Query("""
            select q from QuestionEntity q
            where q.schoolId = :schoolId
              and (:topicId is null or q.topicId = :topicId)
              and (:difficulty is null or q.difficulty = :difficulty)
            order by q.id
            """)
    List<QuestionEntity> search(@Param("schoolId") Long schoolId,
                                @Param("topicId") Long topicId,
                                @Param("difficulty") Integer difficulty);
}
