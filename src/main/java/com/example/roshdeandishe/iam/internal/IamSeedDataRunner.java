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

    /**
     * به‌ازای هر کد idempotent - همان دلیل seedPermissions پایین: early-return
     * قبلی روی count یعنی هر Module جدید (اولین مورد واقعی: CURRICULUM،
     * پورت moshavere) هرگز به یک دیتابیس dev از قبل seed شده نمی‌رسید و
     * @RequiresModule آن برای همیشه 403 می‌داد، در حالی که تست‌ها
     * (Testcontainers همیشه تازه) سبز می‌ماندند.
     */
    private void seedModules() {
        module("ACADEMIC", "آموزش", ModuleScope.SCHOOL);
        module("FINANCE", "مالی", ModuleScope.SCHOOL);
        module("CURRICULUM", "سرفصل‌ها و بانک سؤال", ModuleScope.SCHOOL);
        module("STUDY_PLANNER", "برنامه‌ریز مطالعه", ModuleScope.SCHOOL);
        // SCHOOL-scoped عمداً، نه استفاده‌ی مجدد از EXAM_PREP (که USER-scoped/B2C
        // است): آزمون در moshavere تألیف‌شده توسط مدرس و per-school بود - همان
        // شکل B2B ماژول‌های دیگر، نه اشتراک فردی (MOSHAVERE_PORT_SPEC.md §5)
        module("EXAM", "آزمون", ModuleScope.SCHOOL);
        module("LIBRARY", "کتابخانه", ModuleScope.SCHOOL);
        module("ATTENDANCE", "حضور و غیاب", ModuleScope.SCHOOL);
        module("TRANSPORTATION", "سرویس", ModuleScope.SCHOOL);

        module("AI_TUTOR", "معلم هوش مصنوعی", ModuleScope.USER);
        module("EXAM_PREP", "آماده‌سازی آزمون", ModuleScope.USER);
        module("PORTFOLIO_BUILDER", "ساخت نمونه‌کار", ModuleScope.USER);
        module("TEACHER_MARKETPLACE", "بازار معلم‌ها", ModuleScope.USER);
        module("PROFESSIONAL_DEVELOPMENT", "دوره‌های توسعه‌ی حرفه‌ای", ModuleScope.USER);
    }

    private void module(String code, String displayName, ModuleScope scope) {
        if (moduleRepository.findByCode(code).isEmpty()) {
            moduleRepository.save(new ModuleEntity(code, displayName, scope));
        }
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
        permission(permissions, "CURRICULUM_VIEW", "مشاهده‌ی سرفصل‌ها و بانک سؤال");
        permission(permissions, "CURRICULUM_MANAGE", "ساخت سرفصل و سؤال در بانک سؤال");
        permission(permissions, "STUDYPLAN_VIEW", "مشاهده‌ی برنامه‌ی درسی و جدول مطالعه");
        permission(permissions, "STUDYPLAN_MANAGE", "ایجاد/مدیریت برنامه‌ی درسی و جدول مطالعه");
        permission(permissions, "EXAM_VIEW", "مشاهده‌ی آزمون‌ها و نتایج");
        permission(permissions, "EXAM_MANAGE", "تألیف/تخصیص آزمون و ساخت بخش‌های آن");
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
        // تألیف بانک سؤال کار روزمره‌ی معلم است (همان استدلال STUDENT_ENROLL،
        // نه CLASS_MANAGE) - هر دو Permission ماژول CURRICULUM به TEACHER هم
        // داده می‌شود. STUDENT/PARENT عمداً هیچ‌کدام را ندارند: بانک سؤال
        // گزینه‌ی درست و پاسخ تشریحی را حمل می‌کند؛ دسترسی دانش‌آموز بعداً
        // از طریق ماژول exam میانجی‌گری می‌شود، نه خواندن مستقیم بانک.
        grant(roles.get("TEACHER"), permissions.get("CURRICULUM_VIEW"));
        grant(roles.get("TEACHER"), permissions.get("CURRICULUM_MANAGE"));
        // برنامه‌ریزیِ مطالعه هم مثل سرفصل/سوال کار روزمره‌ی معلم است
        // (MOSHAVERE_PORT_SPEC.md §6/§2 - نویسنده‌ی Plan در moshavere همان Instructor است)
        grant(roles.get("TEACHER"), permissions.get("STUDYPLAN_VIEW"));
        grant(roles.get("TEACHER"), permissions.get("STUDYPLAN_MANAGE"));
        // تألیف/تخصیص آزمون هم مثل سرفصل/برنامه‌ی مطالعه کار روزمره‌ی معلم است
        // (همان استدلال CURRICULUM_*/STUDYPLAN_*). STUDENT/PARENT عمداً هیچ‌کدام
        // را ندارند - دسترسی دانش‌آموز فقط از مسیر «my» + توکن جلسه‌ی آزمون است
        // (MyExamsController/ExamTakeController)، نه این دو Permission.
        grant(roles.get("TEACHER"), permissions.get("EXAM_VIEW"));
        grant(roles.get("TEACHER"), permissions.get("EXAM_MANAGE"));

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
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("CURRICULUM_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("CURRICULUM_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("STUDYPLAN_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("STUDYPLAN_MANAGE"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("EXAM_VIEW"));
        grant(roles.get("SCHOOL_ADMIN"), permissions.get("EXAM_MANAGE"));

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
     * ماژول‌های ACADEMIC، FINANCE و CURRICULUM را برای مدرسه‌ی نمونه (id=1،
     * ساخته‌شده توسط SchoolSeedDataRunner روی دیتابیس تازه) فعال می‌کند -
     * وگرنه کنترلرهای @RequiresModule دار برای جریان dev از همان ابتدا 403
     * می‌دهند. به‌ازای هر ماژول idempotent (نه early-return روی count) -
     * همان تله‌ی seedPermissions/seedModules: وگرنه Entitlement ماژول جدید
     * هرگز به دیتابیس dev از قبل seed شده نمی‌رسید. فقط seed مخصوص dev.
     */
    private void seedDemoSchoolEntitlements() {
        for (String moduleCode : new String[]{"ACADEMIC", "FINANCE", "CURRICULUM", "STUDY_PLANNER", "EXAM"}) {
            moduleRepository.findByCode(moduleCode).ifPresent(module -> {
                boolean exists = schoolEntitlementRepository
                        .findBySchoolIdAndModuleId(DEMO_SCHOOL_ID, module.getId())
                        .isPresent();
                if (!exists) {
                    schoolEntitlementRepository.save(
                            new SchoolEntitlementEntity(DEMO_SCHOOL_ID, module.getId(), true)
                    );
                }
            });
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
