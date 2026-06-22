package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.internal.ModuleEntity.ModuleScope;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * چون پروژه هنوز در فاز توسعه است و ابزار Migration رسمی (مثل Flyway)
 * هنوز معرفی نشده، این Runner به‌صورت idempotent (فقط اگر جدول خالی باشد)
 * مقادیر پایه‌ی Role و Module را درج می‌کند.
 * <p>
 * ⚠️ این فقط برای فاز توسعه مناسب است. وقتی پروژه به production نزدیک شد،
 * این منطق باید با Flyway/Liquibase migration جایگزین شود تا نسخه‌بندی و
 * Auditability درستی روی تغییرات داشته باشیم.
 */
@Component
class IamSeedDataRunner implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;

    IamSeedDataRunner(RoleRepository roleRepository, ModuleRepository moduleRepository) {
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedModules();
    }

    private void seedRoles() {
        if (roleRepository.count() > 0) {
            return;
        }
        roleRepository.save(new RoleEntity("STUDENT", "دانش‌آموز"));
        roleRepository.save(new RoleEntity("TEACHER", "معلم"));
        roleRepository.save(new RoleEntity("PARENT", "والد"));
        roleRepository.save(new RoleEntity("SCHOOL_ADMIN", "مدیر مدرسه"));
        roleRepository.save(new RoleEntity("PLATFORM_ADMIN", "مدیر پلتفرم"));
    }

    private void seedModules() {
        if (moduleRepository.count() > 0) {
            return;
        }
        // ماژول‌های B2B (مدرسه‌محور)
        moduleRepository.save(new ModuleEntity("ACADEMIC", "آموزش", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("FINANCE", "مالی", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("LIBRARY", "کتابخانه", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("ATTENDANCE", "حضور و غیاب", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("TRANSPORTATION", "سرویس", ModuleScope.SCHOOL));

        // ماژول‌های B2C (کاربرمحور)
        moduleRepository.save(new ModuleEntity("AI_TUTOR", "معلم هوش مصنوعی", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("EXAM_PREP", "آماده‌سازی آزمون", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("PORTFOLIO_BUILDER", "ساخت نمونه‌کار", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("TEACHER_MARKETPLACE", "بازار معلم‌ها", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("PROFESSIONAL_DEVELOPMENT", "دوره‌های توسعه‌ی حرفه‌ای", ModuleScope.USER));
    }
}
