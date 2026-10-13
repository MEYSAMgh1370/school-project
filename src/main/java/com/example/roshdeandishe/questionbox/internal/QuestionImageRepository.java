package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface QuestionImageRepository extends JpaRepository<QuestionImageEntity, Long> {

    /**
     * فقط شناسه، سؤال و نقشِ تصویرها، بدون ستونِ bytea: فهرست‌ها و نمای نهایی نباید بایت‌های همه‌ی تصویرهای
     * مدرسه را به heap بیاورند (Hibernate lazy-loading ستون ندارد). بایت‌ها فقط با {@code findById} خوانده می‌شوند.
     */
    @Query("select i.id as id, i.questionId as questionId, i.role as role from QuestionImageEntity i "
            + "where i.questionId in :questionIds order by i.id asc")
    List<ImageMeta> findMetaByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);

    long countByQuestionId(Long questionId);

    interface ImageMeta {

        Long getId();

        Long getQuestionId();

        ImageRole getRole();
    }
}
