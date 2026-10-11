package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    /** سرفصل‌های قابل‌مشاهده برای مدرسه: پلتفرمی (school_id null) + خودِ مدرسه. */
    @Query("select t from TopicEntity t where t.schoolId is null or t.schoolId = :schoolId "
            + "order by t.sortOrder asc, t.id asc")
    List<TopicEntity> findVisibleToSchool(@Param("schoolId") Long schoolId);

    /**
     * شناسه‌ی ریشه + همه‌ی نوادگان قابل‌مشاهده برای مدرسه (پلتفرمی یا متعلق به
     * همین مدرسه). union (نه union all) در برابر حلقه‌ی احتمالیِ داده محافظ است.
     */
    @Query(value = """
            with recursive sub(id) as (
                select t.id from curriculum.topics t where t.id = :rootId
                union
                select c.id from curriculum.topics c
                join sub s on c.parent_id = s.id
                where c.school_id is null or c.school_id = :schoolId
            )
            select id from sub
            """, nativeQuery = true)
    List<Long> findSubtreeIds(@Param("rootId") Long rootId, @Param("schoolId") Long schoolId);
}
