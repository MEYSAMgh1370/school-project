package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository داخلی - package-private نگه‌داشته نمی‌شود چون JPA به interface
 * public نیاز دارد، اما چون پکیج {@code internal} است، Spring Modulith آن را
 * از دید سایر ماژول‌ها مخفی نگه می‌دارد (محدودیت‌های Modulith بر اساس پکیج
 * اعمال می‌شود، نه فقط access modifier جاوا).
 */
interface SchoolModuleRepository extends JpaRepository<SchoolModuleEntity, Long> {

    Optional<SchoolModuleEntity> findBySchoolIdAndModuleName(Long schoolId, String moduleName);
}
