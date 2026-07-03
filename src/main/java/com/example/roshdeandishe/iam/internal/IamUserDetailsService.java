package com.example.roshdeandishe.iam.internal;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security فقط برای اثبات هویت (کاربر کیست) استفاده می‌شود، نه
 * تعیین سطح دسترسی - چون Permissionهای واقعی per-school هستند
 * (SchoolMembership + RolePermission) و توسط ModuleAccessAspect/
 * PermissionAccessAspect به‌صورت پویا از IamFacade خوانده می‌شوند، نه از
 * یک لیست authority ثابت روی Principal.
 * <p>
 * به همین دلیل authorities اینجا عمداً خالی است - یک لیست global از
 * authorityها هیچ معنایی در این دامنه ندارد (کاربری که Teacher در مدرسه A
 * و Student در مدرسه B است، authority سطح global معناداری برایش وجود ندارد).
 */
@Service
class IamUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    IamUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("کاربری با ایمیل " + email + " یافت نشد."));

        if (user.getPasswordHash() == null) {
            // حساب توسط ادمین ساخته شده اما هنوز رمز عبور تنظیم نشده
            throw new UsernameNotFoundException("حساب کاربری برای " + email + " هنوز فعال‌سازی نشده است.");
        }

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(user.getStatus() == UserEntity.UserStatus.SUSPENDED)
                .authorities(Collections.emptyList())
                .build();
    }
}
