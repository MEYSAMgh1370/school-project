package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {

    /** فعالیت‌های خودِ مدرسه به‌علاوه‌ی پیش‌فرض‌های پلتفرم (schoolId خالی). */
    @Query("select a from ActivityEntity a where a.schoolId = :schoolId or a.schoolId is null order by a.name")
    List<ActivityEntity> findVisibleToSchool(Long schoolId);

    boolean existsBySchoolIdAndName(Long schoolId, String name);

    boolean existsBySchoolIdIsNullAndName(String name);

    /** قفلِ یک فعالیت: حذفِ فعالیت و ثبتِ خانه با آن پشت سر هم اجرا می‌شوند. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    int lockActivity(int namespace, int key);
}
