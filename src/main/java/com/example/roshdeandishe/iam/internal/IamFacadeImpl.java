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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
class IamFacadeImpl implements IamFacade {

    private static final int INVITE_TOKEN_BYTES = 32;
    private static final long INVITE_TOKEN_TTL_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // §9: کدهای نقش UPPER_SNAKE_CASE هستند - همین قرارداد روی نقش‌های
    // سفارشی هم اجباری می‌شود تا در کنار نقش‌های سیستمی یکدست بمانند
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    // نقش رزروشده‌ی پلتفرم: نه در listRoles دیده می‌شود و نه در
    // createMembership قابل تخصیص است - هر دو نقطه از همین ثابت استفاده
    // می‌کنند تا این دو قرارداد از هم جدا نیفتند
    private static final String PLATFORM_ADMIN_ROLE_CODE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final SchoolEntitlementRepository schoolEntitlementRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final AccessDecisionService accessDecisionService;
    private final PermissionDecisionService permissionDecisionService;
    private final PasswordEncoder passwordEncoder;
    private final InviteEmailSender inviteEmailSender;
    private final SmsDriver smsDriver;

    IamFacadeImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            ModuleRepository moduleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            SchoolMembershipRepository schoolMembershipRepository,
            SchoolEntitlementRepository schoolEntitlementRepository,
            InviteTokenRepository inviteTokenRepository,
            VerificationCodeRepository verificationCodeRepository,
            AccessDecisionService accessDecisionService,
            PermissionDecisionService permissionDecisionService,
            PasswordEncoder passwordEncoder,
            InviteEmailSender inviteEmailSender,
            SmsDriver smsDriver
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.schoolEntitlementRepository = schoolEntitlementRepository;
        this.inviteTokenRepository = inviteTokenRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.accessDecisionService = accessDecisionService;
        this.permissionDecisionService = permissionDecisionService;
        this.passwordEncoder = passwordEncoder;
        this.inviteEmailSender = inviteEmailSender;
        this.smsDriver = smsDriver;
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
    public boolean isPlatformAdmin(UserId userId) {
        return permissionDecisionService.isPlatformAdmin(userId == null ? null : userId.value());
    }

    @Override
    public Optional<UserProfile> findUser(UserId userId) {
        return userRepository.findById(userId.value())
                .map(u -> new UserProfile(new UserId(u.getId()), u.getEmail(), u.getFullName(), u.getPhoneNumber()));
    }

    @Override
    public Optional<UserProfile> findUserByPhoneNumber(String phoneNumber) {
        String canonicalPhoneNumber = PhoneNumberCanonicalizer.canonicalize(phoneNumber);
        if (canonicalPhoneNumber == null || canonicalPhoneNumber.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByPhoneNumber(canonicalPhoneNumber)
                .map(u -> new UserProfile(new UserId(u.getId()), u.getEmail(), u.getFullName(), u.getPhoneNumber()));
    }

    @Override
    @Transactional
    public void setPhoneNumber(UserId userId, String phoneNumber) {
        String canonicalPhoneNumber = PhoneNumberCanonicalizer.canonicalize(phoneNumber);
        if (canonicalPhoneNumber == null || canonicalPhoneNumber.isBlank()
                || canonicalPhoneNumber.length() < 7 || canonicalPhoneNumber.length() > 32) {
            throw new IamException(new IamError.InvalidPhoneNumber("شماره تلفن باید بین ۷ تا ۳۲ کاراکتر باشد."));
        }

        Optional<UserEntity> existingClaim = userRepository.findByPhoneNumber(canonicalPhoneNumber);
        if (existingClaim.isPresent() && !existingClaim.get().getId().equals(userId.value())) {
            throw new IamException(new IamError.PhoneNumberAlreadyClaimed(canonicalPhoneNumber));
        }

        UserEntity user = userRepository.findById(userId.value())
                .orElseThrow(() -> new IamException(new IamError.UserNotFound(userId.value())));
        user.setPhoneNumber(canonicalPhoneNumber);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void requestOtp(String phoneNumber) {
        String canonicalPhoneNumber = PhoneNumberCanonicalizer.canonicalize(phoneNumber);
        if (canonicalPhoneNumber == null || canonicalPhoneNumber.isBlank()) {
            throw new IamException(new IamError.PhoneNotRegistered(canonicalPhoneNumber));
        }
        UserEntity user = userRepository.findByPhoneNumber(canonicalPhoneNumber)
                .orElseThrow(() -> new IamException(new IamError.PhoneNotRegistered(canonicalPhoneNumber)));

        Instant now = Instant.now();
        String code = generateOtp();
        if (!smsDriver.sendOtp(canonicalPhoneNumber, code)) {
            throw new IamException(new IamError.OtpDeliveryUnavailable(canonicalPhoneNumber));
        }

        verificationCodeRepository.save(
                new VerificationCodeEntity(user.getId(), code, now, now.plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES))
        );
    }

    @Override
    @Transactional
    public UserProfile verifyOtp(String phoneNumber, String code) {
        String canonicalPhoneNumber = PhoneNumberCanonicalizer.canonicalize(phoneNumber);
        if (canonicalPhoneNumber == null || canonicalPhoneNumber.isBlank()) {
            throw new IamException(new IamError.PhoneNotRegistered(canonicalPhoneNumber));
        }
        UserEntity user = userRepository.findByPhoneNumber(canonicalPhoneNumber)
                .orElseThrow(() -> new IamException(new IamError.PhoneNotRegistered(canonicalPhoneNumber)));

        // فقط آخرین کدِ مصرف‌نشده معتبر است - رجوع به VerificationCodeRepository
        VerificationCodeEntity verificationCode = verificationCodeRepository
                .findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IamException(new IamError.OtpExpired(canonicalPhoneNumber)));

        if (verificationCode.isExpired(Instant.now())) {
            throw new IamException(new IamError.OtpExpired(canonicalPhoneNumber));
        }
        if (!verificationCode.getCode().equals(code)) {
            // عمداً مصرف‌شده علامت زده نمی‌شود - کاربر می‌تواند تا قبل از
            // انقضا دوباره تلاش کند
            throw new IamException(new IamError.OtpIncorrect(canonicalPhoneNumber));
        }

        verificationCode.setConsumedAt(Instant.now());
        verificationCodeRepository.save(verificationCode);

        return new UserProfile(new UserId(user.getId()), user.getEmail(), user.getFullName(), user.getPhoneNumber());
    }

    private static String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int value = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", value);
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
    public java.util.Set<String> findActiveRoleCodes(UserId userId, Long schoolId) {
        List<Long> roleIds = schoolMembershipRepository.findByUserIdAndSchoolIdAndActiveTrue(userId.value(), schoolId)
                .stream().map(SchoolMembershipEntity::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return java.util.Set.of();
        }
        return roleRepository.findAllById(roleIds).stream().map(RoleEntity::getCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public Long createMembership(UserId userId, Long schoolId, String roleCode) {
        // PLATFORM_ADMIN مسیر بررسی مجوز ندارد (PermissionDecisionService فقط
        // SchoolContext را تصمیم می‌گیرد) و هیچ ردیف role_permissions هم
        // ندارد، پس امروز چیزی به کسی نمی‌دهد. مسئله ترتیب کارهاست: بدون این
        // نگهبان، ردیف‌های عضویت همین حالا انباشته می‌شوند و همان لحظه که
        // مسیر مجوز پلتفرم ساخته شود، ناگهان دسترسی می‌دهند - پاک کردن
        // بعدی‌شان از ردکردن همین حالا بدتر است.
        //
        // عمداً همان RoleNotFound برمی‌گردد که یک کد ناموجود می‌گیرد: قرارداد
        // listRoles/RoleLabels این است که این کد در هیچ UI ای ظاهر نشود، پس
        // پاسخ هم نباید وجودش را لو بدهد.
        if (PLATFORM_ADMIN_ROLE_CODE.equals(roleCode)) {
            throw new IamException(new IamError.RoleNotFound(roleCode));
        }

        // نقش سفارشیِ خودِ این مدرسه اولویت دارد، وگرنه نقش سیستمی - نقش
        // سفارشیِ مدرسه‌ی دیگری با همین کد هرگز اینجا resolve نمی‌شود
        // (RoleCodeAlreadyExists در createCustomRole تضمین می‌کند این دو
        // هیچ‌وقت هم‌زمان با کد یکسان وجود نداشته باشند، پس این ترتیب
        // ابهامی ایجاد نمی‌کند)
        RoleEntity role = roleRepository.findByCodeAndSchoolId(roleCode, schoolId)
                .or(() -> roleRepository.findByCodeAndSchoolIdIsNull(roleCode))
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
    public MembershipInfo addMember(String email, String fullName, Long schoolId, String roleCode) {
        // این دو فراخوانی عمداً مستقیم (self-invocation) هستند و از proxy رد
        // نمی‌شوند: به همین دلیل @Transactional خودشان اعمال نمی‌شود و هر دو
        // داخل همین تراکنش اجرا می‌شوند - که دقیقاً هدف است. اگر روزی برای
        // «تمیزتر شدن» به self-injection یا فراخوانی از طریق proxy تغییر
        // کنند، findOrCreateUser دوباره جدا commit می‌کند و باگ issue #80
        // برمی‌گردد: کاربرِ بی‌عضویتِ جامانده بعد از ردِ roleCode.
        UserId userId = findOrCreateUser(email, fullName);
        Long membershipId = createMembership(userId, schoolId, roleCode);

        // همان ردیفی که همین حالا ساخته/فعال شد - نه findMembership که برای
        // کاربرِ چندنقشی در یک مدرسه ممکن است Membership دیگری را برگرداند
        return schoolMembershipRepository.findByIdAndSchoolId(membershipId, schoolId)
                .map(this::toMembershipInfo)
                .orElseThrow(() -> new IamException(new IamError.MembershipIdNotFound(membershipId)));
    }

    @Override
    @Transactional
    public void setMembershipActive(Long schoolId, Long membershipId, boolean active) {
        SchoolMembershipEntity membership = schoolMembershipRepository.findByIdAndSchoolId(membershipId, schoolId)
                .orElseThrow(() -> new IamException(new IamError.MembershipIdNotFound(membershipId)));

        membership.setActive(active);
        schoolMembershipRepository.save(membership);
    }

    @Override
    @Transactional
    public Optional<InviteIssued> issueInviteToken(Long schoolId, Long membershipId) {
        SchoolMembershipEntity membership = schoolMembershipRepository.findByIdAndSchoolId(membershipId, schoolId)
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

    @Override
    @Transactional
    public boolean ensureSchoolEntitlement(Long schoolId, ModuleCode module) {
        if (schoolEntitlementRepository.findBySchoolIdAndModuleId(schoolId, requireSchoolModule(module).getId())
                .isPresent()) {
            return false;
        }
        setSchoolEntitlement(schoolId, module, true);
        return true;
    }

    private ModuleEntity requireSchoolModule(ModuleCode module) {
        return moduleRepository.findByCode(module.value())
                .orElseThrow(() -> new IamException(new IamError.ModuleNotFound(module.value())));
    }

    /**
     * بسته‌ی رایگان (CLAUDE.md §11 Q10): ACADEMIC و FINANCE، و ATTENDANCE (تصمیم ۶ حضور و غیاب).
     * CURRICULUM/STUDY_PLANNER/EXAM پولی می‌مانند و اینجا اعطا نمی‌شوند.
     */
    private static final String[] FREE_TIER_MODULES = {"ACADEMIC", "FINANCE", "ATTENDANCE"};

    @Override
    @Transactional
    public void grantFreeTierEntitlements(Long schoolId) {
        for (String moduleCode : FREE_TIER_MODULES) {
            setSchoolEntitlement(schoolId, new ModuleCode(moduleCode), true);
        }
    }

    @Override
    public List<PermissionInfo> listPermissions() {
        return permissionRepository.findAll().stream()
                // Permissionهای سطح پلتفرم به هیچ نقش مدرسه‌ای تعلق نمی‌گیرند و در چک‌لیست ساخت نقش نیستند
                .filter(p -> !PlatformPermissions.CODES.contains(p.getCode()))
                .map(p -> new PermissionInfo(p.getId(), p.getCode(), p.getDisplayName()))
                .sorted(Comparator.comparing(PermissionInfo::code))
                .toList();
    }

    @Override
    public List<RoleInfo> listRoles(Long schoolId) {
        return roleRepository.findBySchoolIdIsNullOrSchoolId(schoolId).stream()
                // PLATFORM_ADMIN مسیر بررسی مجوز ندارد و نباید در هیچ UI ای
                // ظاهر شود - همان قرارداد web.RoleLabels
                .filter(r -> !PLATFORM_ADMIN_ROLE_CODE.equals(r.getCode()))
                .map(this::toRoleInfo)
                .toList();
    }

    @Override
    public java.util.Map<String, List<String>> findPermissionCodesByRole(Long schoolId,
                                                                         java.util.Collection<String> roleCodes) {
        List<RoleEntity> roles = roleRepository.findBySchoolIdIsNullOrSchoolId(schoolId).stream()
                .filter(r -> !PLATFORM_ADMIN_ROLE_CODE.equals(r.getCode()) && roleCodes.contains(r.getCode()))
                .toList();
        if (roles.isEmpty()) {
            return java.util.Map.of();
        }
        List<RolePermissionEntity> grants = rolePermissionRepository.findByRoleIdIn(
                roles.stream().map(RoleEntity::getId).toList());
        java.util.Map<Long, String> codeByPermissionId = new java.util.HashMap<>();
        permissionRepository.findAllById(grants.stream().map(RolePermissionEntity::getPermissionId).distinct().toList())
                .forEach(p -> codeByPermissionId.put(p.getId(), p.getCode()));
        java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (RoleEntity role : roles) {
            result.put(role.getCode(), grants.stream()
                    .filter(g -> g.getRoleId().equals(role.getId()))
                    .map(g -> codeByPermissionId.get(g.getPermissionId()))
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList());
        }
        return result;
    }

    @Override
    public java.util.Map<String, String> findCustomRoleDisplayNames(Long schoolId,
                                                                    java.util.Collection<String> roleCodes) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        roleRepository.findBySchoolIdIsNullOrSchoolId(schoolId).stream()
                .filter(r -> schoolId.equals(r.getSchoolId()) && roleCodes.contains(r.getCode()))
                .forEach(r -> result.put(r.getCode(), r.getDisplayName()));
        return result;
    }

    @Override
    @Transactional
    public RoleInfo createCustomRole(Long schoolId, String code, String displayName, List<String> permissionCodes) {
        if (code == null || !ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw new IamException(new IamError.InvalidRoleDefinition(
                    "کد نقش باید با حرف بزرگ لاتین شروع شود و فقط شامل حروف بزرگ، عدد و _ باشد."
            ));
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IamException(new IamError.InvalidRoleDefinition("عنوان نقش الزامی است."));
        }
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new IamException(new IamError.InvalidRoleDefinition("حداقل یک Permission باید انتخاب شود."));
        }

        // تداخل با نقش سیستمی یا نقش سفارشیِ همین مدرسه - نه نقش سفارشیِ
        // مدرسه‌ی دیگر (آن‌ها اصلاً برای این مدرسه قابل‌مشاهده نیستند)
        if (roleRepository.findByCodeAndSchoolIdIsNull(code).isPresent()
                || roleRepository.findByCodeAndSchoolId(code, schoolId).isPresent()) {
            throw new IamException(new IamError.RoleCodeAlreadyExists(code));
        }

        List<PermissionEntity> permissions = permissionCodes.stream()
                .distinct()
                .map(pc -> (PlatformPermissions.CODES.contains(pc)
                        ? Optional.<PermissionEntity>empty()
                        : permissionRepository.findByCode(pc))
                        .orElseThrow(() -> new IamException(new IamError.InvalidPermissionCode(pc))))
                .toList();

        RoleEntity role = roleRepository.save(new RoleEntity(code, displayName, schoolId));
        permissions.forEach(p -> rolePermissionRepository.save(new RolePermissionEntity(role.getId(), p.getId())));

        return toRoleInfo(role);
    }

    private RoleInfo toRoleInfo(RoleEntity role) {
        List<String> permissionCodes = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(rp -> permissionRepository.findById(rp.getPermissionId()).map(PermissionEntity::getCode).orElse(null))
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return new RoleInfo(role.getId(), role.getCode(), role.getDisplayName(), role.getSchoolId(), permissionCodes);
    }

    private MembershipInfo toMembershipInfo(SchoolMembershipEntity membership) {
        String roleCode = roleRepository.findById(membership.getRoleId())
                .map(RoleEntity::getCode)
                .orElseThrow(() -> new IamException(
                        new IamError.MembershipNotFound(membership.getUserId(), membership.getSchoolId())
                ));

        UserEntity user = userRepository.findById(membership.getUserId())
                .orElseThrow(() -> new IamException(new IamError.UserNotFound(membership.getUserId())));

        return new MembershipInfo(
                membership.getId(),
                new UserId(membership.getUserId()),
                user.getEmail(),
                user.getFullName(),
                membership.getSchoolId(),
                roleCode,
                membership.isActive(),
                user.getPasswordHash() != null
        );
    }
}
