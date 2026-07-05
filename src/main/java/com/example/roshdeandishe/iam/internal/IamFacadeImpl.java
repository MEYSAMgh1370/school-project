package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.internal.ModuleEntity.ModuleScope;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
class IamFacadeImpl implements IamFacade {

    private static final int INVITE_TOKEN_BYTES = 32;
    private static final long INVITE_TOKEN_TTL_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final SchoolEntitlementRepository schoolEntitlementRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final AccessDecisionService accessDecisionService;
    private final PermissionDecisionService permissionDecisionService;
    private final PasswordEncoder passwordEncoder;
    private final InviteEmailSender inviteEmailSender;

    IamFacadeImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            ModuleRepository moduleRepository,
            SchoolMembershipRepository schoolMembershipRepository,
            SchoolEntitlementRepository schoolEntitlementRepository,
            InviteTokenRepository inviteTokenRepository,
            AccessDecisionService accessDecisionService,
            PermissionDecisionService permissionDecisionService,
            PasswordEncoder passwordEncoder,
            InviteEmailSender inviteEmailSender
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.schoolEntitlementRepository = schoolEntitlementRepository;
        this.inviteTokenRepository = inviteTokenRepository;
        this.accessDecisionService = accessDecisionService;
        this.permissionDecisionService = permissionDecisionService;
        this.passwordEncoder = passwordEncoder;
        this.inviteEmailSender = inviteEmailSender;
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
    @Transactional
    public UserId registerUser(String email, String fullName, String password) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IamException(new IamError.InvalidRegistration("ایمیل معتبر نیست."));
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IamException(new IamError.InvalidRegistration("نام و نام خانوادگی الزامی است."));
        }
        if (password == null || password.isBlank() || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IamException(new IamError.InvalidPassword(
                    "رمز عبور باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد."
            ));
        }

        // ایمیلِ موجود - حتی حساب دعوت‌شده‌ی فعال‌نشده - همیشه رد می‌شود؛
        // مسیر مجاز برای آن حساب‌ها فقط توکن دعوت است (اثبات مالکیت ایمیل).
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IamException(new IamError.EmailAlreadyRegistered(email));
        }

        UserEntity user = new UserEntity(email, fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        return new UserId(userRepository.save(user).getId());
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
    public List<MembershipInfo> findMembershipsByUser(UserId userId) {
        return schoolMembershipRepository.findByUserId(userId.value()).stream()
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
    public Optional<InviteIssued> issueInviteToken(Long membershipId) {
        SchoolMembershipEntity membership = schoolMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new IamException(new IamError.MembershipIdNotFound(membershipId)));

        UserEntity user = userRepository.findById(membership.getUserId())
                .orElseThrow(() -> new IamException(new IamError.UserNotFound(membership.getUserId())));

        if (user.getPasswordHash() != null) {
            // قبلاً فعال شده - نه خطا، فقط کاری برای انجام دادن نیست
            return Optional.empty();
        }

        Instant now = Instant.now();
        String token = inviteTokenRepository
                .findFirstByUserIdAndRedeemedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), now)
                .map(InviteTokenEntity::getToken)
                .orElseGet(() -> {
                    String fresh = generateToken();
                    Instant expiresAt = now.plus(INVITE_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
                    inviteTokenRepository.save(new InviteTokenEntity(user.getId(), fresh, now, expiresAt));
                    return fresh;
                });

        // ارسال ایمیل در هر دو حالت (توکن تازه یا موجود) - صدور مجدد یعنی
        // «ایمیل دعوت را دوباره بفرست». شکست ارسال، صدور را باطل نمی‌کند.
        boolean emailSent = inviteEmailSender.sendActivationEmail(user.getEmail(), user.getFullName(), token);

        return Optional.of(new InviteIssued(token, emailSent));
    }

    @Override
    @Transactional
    public void redeemInviteToken(String token, String newPassword) {
        InviteTokenEntity inviteToken = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new IamException(new IamError.InviteTokenNotFound(token)));

        if (inviteToken.isRedeemed()) {
            throw new IamException(new IamError.InviteTokenAlreadyRedeemed(token));
        }
        if (inviteToken.isExpired(Instant.now())) {
            throw new IamException(new IamError.InviteTokenExpired(token));
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IamException(new IamError.InvalidPassword(
                    "رمز عبور باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد."
            ));
        }

        UserEntity user = userRepository.findById(inviteToken.getUserId())
                .orElseThrow(() -> new IamException(new IamError.UserNotFound(inviteToken.getUserId())));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        inviteToken.setRedeemedAt(Instant.now());
        inviteTokenRepository.save(inviteToken);
    }

    private static String generateToken() {
        byte[] bytes = new byte[INVITE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
