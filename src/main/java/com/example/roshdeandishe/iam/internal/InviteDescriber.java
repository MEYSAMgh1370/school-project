package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.IamFacade.InviteLookup;
import com.example.roshdeandishe.iam.IamFacade.InviteMembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.InviteState;
import com.example.roshdeandishe.iam.shared.SchoolNameSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * توضیحِ یک دعوت‌نامه برای صفحه‌ی فعال‌سازی و متنِ ایمیل: «به کدام مدرسه و با چه نقشی». نامِ مدرسه از
 * {@link SchoolNameSource} می‌آید (iam به school وابسته نیست)؛ پیاده‌سازی با {@code ObjectProvider} و
 * در لحظه‌ی استفاده گرفته می‌شود تا ساختِ bean حلقه نسازد.
 * <p>
 * فقط وضعیتِ توکن برای حالت‌های غیرمعتبر برمی‌گردد (نه ایمیل/نام): کسی که توکنِ مصرف‌شده یا منقضی دارد چیزی
 * از حساب نمی‌فهمد. برای توکنِ معتبر، خودِ توکن همان رازِ دسترسی است.
 */
@Service
class InviteDescriber {

    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final SchoolMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final ObjectProvider<SchoolNameSource> schoolNames;

    InviteDescriber(InviteTokenRepository inviteTokenRepository, UserRepository userRepository,
                    SchoolMembershipRepository membershipRepository, RoleRepository roleRepository,
                    ObjectProvider<SchoolNameSource> schoolNames) {
        this.inviteTokenRepository = inviteTokenRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.schoolNames = schoolNames;
    }

    @Transactional(readOnly = true)
    InviteLookup describe(String token) {
        if (token == null || token.isBlank()) {
            return InviteLookup.of(InviteState.UNKNOWN);
        }
        Optional<InviteTokenEntity> found = inviteTokenRepository.findByToken(token.trim());
        if (found.isEmpty()) {
            return InviteLookup.of(InviteState.UNKNOWN);
        }
        InviteTokenEntity invite = found.get();
        if (invite.isRedeemed()) {
            return InviteLookup.of(InviteState.USED);
        }
        if (invite.isExpired(Instant.now())) {
            return InviteLookup.of(InviteState.EXPIRED);
        }
        Optional<UserEntity> user = userRepository.findById(invite.getUserId());
        if (user.isEmpty()) {
            return InviteLookup.of(InviteState.UNKNOWN);
        }
        List<InviteMembershipInfo> memberships = membershipRepository.findByUserId(user.get().getId()).stream()
                .filter(SchoolMembershipEntity::isActive)
                .map(m -> new InviteMembershipInfo(
                        schoolName(m.getSchoolId()).orElse(null), roleLabel(m)))
                .toList();
        return new InviteLookup(InviteState.VALID, user.get().getEmail(), user.get().getFullName(), memberships);
    }

    /** «معلم در دبستان آزمایشی» (یا فقط «معلم» وقتی نامِ مدرسه در دسترس نیست). */
    String summaryFor(SchoolMembershipEntity membership) {
        String role = roleLabel(membership);
        return schoolName(membership.getSchoolId()).map(name -> role + " در " + name).orElse(role);
    }

    private Optional<String> schoolName(Long schoolId) {
        SchoolNameSource source = schoolNames.getIfAvailable();
        return source == null ? Optional.empty() : source.nameOf(schoolId);
    }

    private String roleLabel(SchoolMembershipEntity membership) {
        return roleRepository.findById(membership.getRoleId()).map(RoleEntity::getDisplayName).orElse("عضو");
    }
}
