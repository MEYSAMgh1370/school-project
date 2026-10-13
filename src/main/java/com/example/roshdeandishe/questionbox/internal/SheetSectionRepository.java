package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface SheetSectionRepository extends JpaRepository<SheetSectionEntity, Long> {

    List<SheetSectionEntity> findBySheetIdOrderByPositionAsc(Long sheetId);

    List<SheetSectionEntity> findBySheetIdInOrderByPositionAsc(Collection<Long> sheetIds);

    /** آیتم‌ها با کلید خارجی {@code on delete cascade} همراه بخش حذف می‌شوند. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SheetSectionEntity s where s.sheetId = :sheetId")
    void deleteBySheetId(@Param("sheetId") Long sheetId);
}
