package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.internal.ModuleEntity.ModuleScope;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Seed داده‌ی dev. علاوه بر Role/Module قبلی، حالا Permission و
 * RolePermission هم seed می‌شوند تا نمونه‌ی واقعی "دو نقش با سطح دسترسی
 * متفاوت" در دیتابیس وجود داشته باشد: SCHOOL_ADMIN (دسترسی کامل) در مقابل
 * FINANCE_ADMIN (فقط دسترسی مالی) - بدون اینکه هیچ کدی برای این تفاوت
 * نوشته شده باشد، فقط داده.
 * <p>
 * از این فاز به بعد، یک کاربر ادمین نمونه هم seed می‌شود
 * (admin@roshdeandishe.test / password: "password") تا لاگین session-based
 * از ابتدا قابل تست باشد. این فقط برای dev است - در production باید
 * حذف شود (یا پشت یک profile مثل "dev" قرار بگیرد).
 */
@Component
class IamSeedDataRunner implements ApplicationRunner {

    private static final String DEMO_ADMIN_EMAIL = "admin@roshdeandishe.test";
    private static final String DEMO_ADMIN_PASSWORD = "password";
    private static final Long DEMO_SCHOOL_ID = 1L;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SchoolEntitlementRepository schoolEntitlementRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final PasswordEncoder passwordEncoder;

    IamSeedDataRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            ModuleRepository moduleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            SchoolEntitlementRepository schoolEntitlementRepository,
            SchoolMembershipRepository schoolMembershipRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.schoolEntitlementRepository = schoolEntitlementRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedModules();
        Map<String, RoleEntity> roles = seedRoles();
        Map<String, PermissionEntity> permissions = seedPermissions();
        seedRolePermissions(roles, permissions);
        seedDemoAdminUser();
        seedDemoSchoolEntitlements();
        seedDemoAdminMembership();
    }

    private void seedModules() {
        if (moduleRepository.count() > 0) {
            return;
        }
        moduleRepository.save(new ModuleEntity("ACADEMIC", "آموزش", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("FINANCE", "مالی", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("LIBRARY", "کتابخانه", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("ATTENDANCE", "حضور و غیاب", ModuleScope.SCHOOL));
        moduleRepository.save(new ModuleEntity("TRANSPORTATION", "سرویس", ModuleScope.SCHOOL));

        moduleRepository.save(new ModuleEntity("AI_TUTOR", "معلم هوش مصنوعی", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("EXAM_PREP", "آماده‌سازی آزمون", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("PORTFOLIO_BUILDER", "ساخت نمونه‌کار", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("TEACHER_MARKETPLACE", "بازار معلم‌ها", ModuleScope.USER));
        moduleRepository.save(new ModuleEntity("PROFESSIONAL_DEVELOPMENT", "دوره‌های توسعه‌ی حرفه‌ای", ModuleScope.USER));
    }

    private Map<String, RoleEntity> seedRoles() {
        if (roleRepository.count() > 0) {
            return roleRepository.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(RoleEntity::getCode, r -> r));
        }

        Map<String, RoleEntity> roles = new java.util.LinkedHashMap<>();
        roles.put("STUDENT", roleRepository.save(new RoleEntity("STUDENT", "دانش‌آموز")));
        roles.put("TEACHER", roleRepository.save(new RoleEntity("TEACHER", "معلم")));
        roles.put("PARENT", roleRepository.save(new RoleEntity("PARENT", "والد")));
        roles.put("SCHOOL_ADMIN", roleRepository.save(new RoleEntity("SCHOOL_ADMIN", "مدیر کامل مدرسه")));
        // نمونه‌ی نقش با سطح دسترسی محدودتر نسبت به SCHOOL_ADMIN - فقط مالی
        roles.put("FINANCE_ADMIN", roleRepository.save(new RoleEntity("FINANCE_ADMIN", "مدیر مالی مدرسه")));
        roles.put("PLATFORM_ADMIN", roleRepository.save(new RoleEntity("PLATFORM_ADMIN", "مدیر پلتفرم")));
        return roles;
    }

    /**
     * برخلاف seedModules/seedRoles (که early-return روی count دارند)، این
     * متد به‌ازای هر کد idempotent است: early-return قبلی یعنی هر Permission
     * جدید هرگز به یک دیتابیس dev از قبل seed شده نمی‌رسید - تست‌ها سبز
     * (Testcontainers همیشه تازه است) ولی هر dev واقعی 403 می‌گرفت. این تله
     * دقیقاً موقع اضافه شدن EXPENSE_* پیدا و بسته شد؛ CLASS_VIEW و
     * GUARDIAN_MANAGE قبلی فقط با reset دیتابیس از آن جان به در برده بودند.
     */
    private Map<String, PermissionEntity> seedPermissions() {
        Map<String, PermissionEntity> permissions = new java.util.LinkedHashMap<>();
        permission(permissions, "GRADES_VIEW", "مشاهده نمرات");
        permission(permissions, "GRADES_EDIT", "ثبت/ویرایش نمرات");
        permission(permissions, "FINANCE_VIEW", "مشاهده وضعیت مالی");
        permission(permissions, "FINANCE_MANAGE", "صدور/مدیریت فاکتور");
        permission(permissions, "MEMBERSHIP_MANAGE", "مدیریت عضویت‌ها (فعال/غیرفعال‌سازی دسترسی)");
        permission(permissions, "STUDENT_ENROLL", "ثبت‌نام دانش‌آموز در کلاس");
        permission(permissions, "CLASS_MANAGE", "ایجاد و مدیریت کلاس‌ها");
        permission(permissions, "CLASS_VIEW", "مشاهده‌ی فهرست کلاس‌ها");
        permission(permissions, "GUARDIAN_MANAGE", "لینک کردن ولی به دانش‌آموز");
        permission(permissions, "EXPENSE_VIEW", "مشاهده‌ی هزینه‌های مدرسه");
        permission(permissions, "EXPENSE_MANAGE", "ثبت/مدیریت هزینه‌های مدرسه");
        permission(permissions, "EXPENSE_APPROVE", "تأیید هزینه‌های مدرسه");
        permission(permissions, "ROLE_MANAGE", "ساخت نقش سفارشی و مدیریت دسترسی‌ها");
        return permissions;
    }

    private void permission(Map<String, PermissionEntity> permissions, String code, String displayName) {
        permissions.put(code, permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new PermissionEntity(code, displayName))));
    }

    private void seedRolePermissions(Map<String, RoleEntity> roles, Map<String, PermissionEntity> permissions) {
        grant(roles.get("STUDENT"), permissions.get("GRADES_VIEW"));

        grant(roles.get("TEACHER"), permissions.get("GRADES_VIEW"));
        grant(roles.get("TEACHER"), permissions.get("GRADES_EDIT"));
        // طبق تصمیم صریح: ثبت‌نام دانش‌آموز، هم برای SCHOOL_ADMIN هم TEACHER مجاز است
        grant(roles.get("TEACHER"), permissions.get("STUDENT_ENROLL"));
        // بدون این، TEACHER نمی‌تواند فهرست کلاس‌ها را برای پر کردن دراپ‌داون
        // انتخاب کلاس در صفحه‌ی ثبت‌نام (/students) ببیند - ساخت کلاس همچنان
        // فقط با CLASS_MANAGE (پایین‌تر) مخصوص SCHOOL_ADMIN می‌ماند
        grant(roles.get("TEACHER"), permissions.get("CLASS_VIEW"));

        grant(roles.get("PARENT"), permissions.get("GRADES_VIEW"));
        grant(roles.get("PARENT"), permissions.get("FINANCE_VIEW"));

        // SCHOOL_ADMIN: دسترسی کامل
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("GRADES_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("FINANCE_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("FINANCE_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("MEMBERSHIP_MANAGE"));
        // ساخت کلاس یک تصمیم چیدمانی/اداری است، فرض شده فقط کار SCHOOL_ADMIN
        // است، نه TEACHER (بر خلاف ثبت‌نام دانش‌آموز که هر دو مجازند)
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("CLASS_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("CLASS_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("STUDENT_ENROLL"));
        // لینک کردن ولی به دانش‌آموز، دسترسی به داده‌ی مالی/تحصیلی آن دانش‌آموز
        // را به کاربر دیگری می‌دهد - هم‌سطح حساسیتِ MEMBERSHIP_MANAGE، پس فقط
        // SCHOOL_ADMIN (نه TEACHER، بر خلاف STUDENT_ENROLL)
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("GUARDIAN_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("EXPENSE_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("EXPENSE_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("EXPENSE_APPROVE"));
        // ساخت/مدیریت نقش سفارشی همان سطح حساسیتِ MEMBERSHIP_MANAGE را دارد -
        // فقط SCHOOL_ADMIN، نه FINANCE_ADMIN (§11 Q3، حل‌شده ۲۰۲۶-۰۷-۱۳)
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("ROLE_MANAGE"));

        // FINANCE_ADMIN: فقط دسترسی مالی - نمونه‌ی واقعی "نقش متفاوت با سطح دسترسی متفاوت".
        // عمداً بدون EXPENSE_APPROVE: تفکیک وظایف - کسی که هزینه را ثبت/پرداخت
        // می‌کند، تأییدکننده‌اش نیست (فقط SCHOOL_ADMIN تأیید می‌کند).
        grant(roles.get("FINANCE_ADMIN"), permissions.get("FINANCE_VIEW"));
        grant(roles.get("FINANCE_ADMIN"), permissions.get("FINANCE_MANAGE"));
        grant(roles.get("FINANCE_ADMIN"), permissions.get("EXPENSE_VIEW"));
        grant(roles.get("FINANCE_ADMIN"), permissions.get("EXPENSE_MANAGE"));
    }

    private void grant(RoleEntity role, PermissionEntity permission) {
        // idempotent به‌ازای هر جفت - همان دلیل seedPermissions بالا
        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
            rolePermissionRepository.save(new RolePermissionEntity(role.getId(), permission.getId()));
        }
    }

    private void seedDemoAdminUser() {
        if (userRepository.findByEmail(DEMO_ADMIN_EMAIL).isPresent()) {
            return;
        }
        UserEntity admin = new UserEntity(DEMO_ADMIN_EMAIL, "ادمین نمونه");
        admin.setPasswordHash(passwordEncoder.encode(DEMO_ADMIN_PASSWORD));
        userRepository.save(admin);
    }

    /**
     * ماژول‌های ACADEMIC و FINANCE را برای مدرسه‌ی نمونه (id=1، ساخته‌شده
     * توسط SchoolSeedDataRunner روی دیتابیس تازه) فعال می‌کند - وگرنه
     * FinanceController (اولین کنترلر با @RequiresModule) برای جریان dev
     * از همان ابتدا 403 می‌دهد. مثل بقیه‌ی این کلاس، فقط seed مخصوص dev.
     */
    private void seedDemoSchoolEntitlements() {
        if (schoolEntitlementRepository.count() > 0) {
            return;
        }
        Long demoSchoolId = 1L;
        for (String moduleCode : new String[]{"ACADEMIC", "FINANCE"}) {
            moduleRepository.findByCode(moduleCode).ifPresent(module ->
                    schoolEntitlementRepository.save(
                            new SchoolEntitlementEntity(demoSchoolId, module.getId(), true)
                    )
            );
        }
    }

    /**
     * ادمین نمونه بدون Membership در مدرسه‌ی نمونه عملاً هیچ API مدرسه‌محوری
     * را نمی‌تواند صدا بزند (PermissionDecisionService عضویت فعال می‌خواهد) -
     * شکافی که تا قبل از ساخت داشبورد به چشم نیامده بود، چون مدرسه‌ی نمونه
     * مستقیم با Repository ساخته می‌شود نه از مسیر خودسرویسی SchoolController
     * (که سازنده را خودکار SCHOOL_ADMIN می‌کند). فقط seed مخصوص dev.
     */
    private void seedDemoAdminMembership() {
        userRepository.findByEmail(DEMO_ADMIN_EMAIL).ifPresent(admin ->
                roleRepository.findByCodeAndSchoolIdIsNull("SCHOOL_ADMIN").ifPresent(role -> {
                    Long demoSchoolId = 1L;
                    boolean exists = schoolMembershipRepository
                            .findByUserIdAndSchoolIdAndRoleId(admin.getId(), demoSchoolId, role.getId())
                            .isPresent();
                    if (!exists) {
                        schoolMembershipRepository.save(
                                new SchoolMembershipEntity(admin.getId(), demoSchoolId, role.getId())
                        );
                    }
                })
        );
    }
}
