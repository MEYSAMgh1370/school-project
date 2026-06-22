package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * این تست مهم‌ترین ابزار برای پایبندی تیم به معماری Modular Monolith است.
 * <p>
 * {@code ApplicationModules.verify()} به‌صورت خودکار بررسی می‌کند که:
 * - هیچ ماژولی مستقیماً به پکیج internal ماژول دیگر وابسته نشده باشد.
 * - وابستگی‌های بین ماژول‌ها فقط از طریق Facade یا Named Interfaceهای علنی‌شده باشد.
 * - گراف وابستگی بین ماژول‌ها چرخه (Cycle) نداشته باشد.
 * <p>
 * توصیه: این تست باید در CI Pipeline اجباری باشد؛ هر PR که این تست را خراب کند
 * عملاً مرز ماژولار پروژه را نقض کرده است.
 */
class ModularityTests {

    @Test
    void verifyModularStructure() {
        ApplicationModules modules = ApplicationModules.of(RoshdeandisheApplication.class);
        modules.verify();
    }

    @Test
    void printModuleStructure() {
        ApplicationModules modules = ApplicationModules.of(RoshdeandisheApplication.class);
        modules.forEach(System.out::println);
    }
}
