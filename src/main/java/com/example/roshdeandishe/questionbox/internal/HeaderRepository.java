package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface HeaderRepository extends JpaRepository<HeaderEntity, Long> {

    Optional<HeaderEntity> findByIdAndSchoolId(Long id, Long schoolId);

    List<HeaderEntity> findBySchoolIdOrderByIdDesc(Long schoolId);

    String ROW = "select new com.example.roshdeandishe.questionbox.internal.HeaderRow(h.id, h.schoolId, "
            + "h.createdByUserId, h.name, h.mode, h.layout, h.showSchoolName, h.showLogo, h.fields, "
            + "case when h.image is null then false else true end) from HeaderEntity h ";

    @Query(ROW + "where h.schoolId = :schoolId order by h.id desc")
    List<HeaderRow> findRowsBySchoolId(@Param("schoolId") Long schoolId);

    @Query(ROW + "where h.id = :id and h.schoolId = :schoolId")
    Optional<HeaderRow> findRowByIdAndSchoolId(@Param("id") Long id, @Param("schoolId") Long schoolId);
}
