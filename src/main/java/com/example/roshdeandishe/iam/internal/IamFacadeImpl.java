package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.internal.ModuleEntity.ModuleScope;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class IamFacadeImpl implements IamFacade {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final SchoolEntitlementRepository schoolEntitlementRepository;
    private final AccessDecisionService accessDecisionService;
    private final PermissionDecisionService permissionDecisionService;

    IamFacadeImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            ModuleRepository moduleRepository,
            SchoolMembershipRepository schoolMembershipRepository,
            SchoolEntitlementRepository schoolEntitlementRepository,
            AccessDecisionService accessDecisionService,
            PermissionDecisionService permissionDecisionService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.schoolEntitlementRepository = schoolEntitlementRepository;
        this.accessDecisionService = accessDecisionService;
        this.permissionDecisionService = permissionDecisionService;
    }

    @Override
    public UserId currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IamException(new IamError.UserNotFound(null));
        }

        String email = auth.getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IamException(new IamError.UserNotFound(null)));

        return new UserId(user.getId());
    }

    @Override
    public AccessDecision checkAccess(UserId userId, ModuleCode module, AccessContext context) {
        return accessDecisionService.decide(userId.value(), module.value(), context);
    }

    @Override
    public AccessDecision checkPermission(UserId userId, PermissionCode permission, AccessContext context) {
        return permissionDecisionService.decide(userId.value(), permission.value(), context);
    }

    @Override
    public Optional<UserProfile> findUser(UserId userId) {
        return userRepository.findById(userId.value())
                .map(u -> new UserProfile(new UserId(u.getId()), u.getEmail(), u.getFullName()));
    }

    @Override
    @Transactional
    public UserId findOrCreateUser(String email, String fullName) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new UserEntity(email, fullName)));
        return new UserId(user.getId());
    }

    @Override
    public Optional<MembershipInfo> findMembership(UserId userId, Long schoolId) {
        List<SchoolMembershipEntity> memberships =
                schoolMembershipRepository.findByUserIdAndSchoolId(userId.value(), schoolId);

        if (memberships.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(toMembershipInfo(memberships.get(0)));
    }

    @Override
    public List<MembershipInfo> findMembershipsBySchool(Long schoolId) {
        return schoolMembershipRepository.findBySchoolId(schoolId).stream()
                .map(this::toMembershipInfo)
                .toList();
    }

    @Override
    @Transactional
    public Long createMembership(UserId userId, Long schoolId, String roleCode) {
        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IamException(new IamError.RoleNotFound(roleCode)));

        // idempotent: اگر همین (user, school, role) از قبل وجود دارد - حتی
        // غیرفعال - دوباره فعالش می‌کنیم به‌جای ساخت ردیف تکراری
        SchoolMembershipEntity membership = schoolMembershipRepository
                .findByUserIdAndSchoolIdAndRoleId(userId.value(), schoolId, role.getId())
                .orElseGet(() -> new SchoolMembershipEntity(userId.value(), schoolId, role.getId()));

        membership.setActive(true);
        SchoolMembershipEntity saved = schoolMembershipRepository.save(membership);
        return saved.getId();
    }

    @Override
    @Transactional
    public void setMembershipActive(Long membershipId, boolean active) {
        SchoolMembershipEntity membership = schoolMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new IamException(new IamError.MembershipIdNotFound(membershipId)));

        membership.setActive(active);
        schoolMembershipRepository.save(membership);
    }

    @Override
    @Transactional
    public void setSchoolEntitlement(Long schoolId, ModuleCode module, boolean active) {
        ModuleEntity moduleEntity = moduleRepository.findByCode(module.value())
                .orElseThrow(() -> new IamException(new IamError.ModuleNotFound(module.value())));

        if (moduleEntity.getScope() != ModuleScope.SCHOOL) {
            throw new IamException(new IamError.ModuleScopeMismatch(
                    module.value(), ModuleScope.SCHOOL.name(), moduleEntity.getScope().name()
            ));
        }

        // idempotent: ردیف موجود را به‌روزرسانی می‌کنیم، نه اینکه هر بار
        // یک ردیف جدید بسازیم (وگرنه unique constraint روی schoolId+moduleId
        // در فراخوانی دوم خطا می‌دهد)
        SchoolEntitlementEntity entitlement = schoolEntitlementRepository
                .findBySchoolIdAndModuleId(schoolId, moduleEntity.getId())
                .orElseGet(() -> new SchoolEntitlementEntity(schoolId, moduleEntity.getId(), active));

        entitlement.setActive(active);
        schoolEntitlementRepository.save(entitlement);
    }

    private MembershipInfo toMembershipInfo(SchoolMembershipEntity membership) {
        String roleCode = roleRepository.findById(membership.getRoleId())
                .map(RoleEntity::getCode)
                .orElseThrow(() -> new IamException(
                        new IamError.MembershipNotFound(membership.getUserId(), membership.getSchoolId())
                ));

        return new MembershipInfo(
                membership.getId(),
                new UserId(membership.getUserId()),
                membership.getSchoolId(),
                roleCode,
                membership.isActive()
        );
    }
}
