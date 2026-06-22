package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.IamError;
import com.example.roshdeandishe.iam.shared.IamException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * پیاده‌سازی واقعی {@link IamFacade}. package-private عمداً، تا حتی در
 * صورت import اشتباه از ماژول‌های دیگر، کامپایلر اجازه استفاده مستقیم
 * نمی‌دهد - تنها از طریق اینترفیس public در iam قابل دسترسی است.
 */
@Service
class IamFacadeImpl implements IamFacade {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final AccessDecisionService accessDecisionService;

    IamFacadeImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            SchoolMembershipRepository schoolMembershipRepository,
            AccessDecisionService accessDecisionService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.accessDecisionService = accessDecisionService;
    }

    @Override
    public UserId currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IamException(new IamError.UserNotFound(null));
        }

        // فرض: principal name همان email کاربر است (مطابق پیکربندی Security پروژه)
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
    public Optional<UserProfile> findUser(UserId userId) {
        return userRepository.findById(userId.value())
                .map(u -> new UserProfile(new UserId(u.getId()), u.getEmail(), u.getFullName()));
    }

    @Override
    public Optional<MembershipInfo> findMembership(UserId userId, Long schoolId) {
        List<SchoolMembershipEntity> memberships =
                schoolMembershipRepository.findByUserIdAndSchoolId(userId.value(), schoolId);

        if (memberships.isEmpty()) {
            return Optional.empty();
        }

        // اگر کاربر چند نقش در همان مدرسه داشته باشد، فعلاً اولین مورد برگردانده می‌شود.
        SchoolMembershipEntity membership = memberships.get(0);
        String roleCode = roleRepository.findById(membership.getRoleId())
                .map(RoleEntity::getCode)
                .orElseThrow(() -> new IamException(
                        new IamError.MembershipNotFound(userId.value(), schoolId)
                ));

        return Optional.of(new MembershipInfo(userId, schoolId, roleCode));
    }
}
