package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface BoxQuestionRepository extends JpaRepository<BoxQuestionEntity, Long> {

    Optional<BoxQuestionEntity> findByIdAndSchoolIdAndDeletedAtIsNull(Long id, Long schoolId);

    /** شاملِ حذف‌شده‌ها: برای نمایشِ برگه‌های ذخیره‌شده. */
    List<BoxQuestionEntity> findByIdInAndSchoolId(Collection<Long> ids, Long schoolId);

    List<BoxQuestionEntity> findBySchoolIdAndDeletedAtIsNullOrderByIdDesc(Long schoolId);
}
