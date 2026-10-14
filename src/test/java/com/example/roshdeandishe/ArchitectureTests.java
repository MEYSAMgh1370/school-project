package com.example.roshdeandishe;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * قوانین معماریِ مکمل ModularityTests. ModularityTests مرز بین ماژول‌ها را
 * می‌سنجد؛ این‌جا قوانین داخل ماژول و سبک کدنویسی است. هر قانون فقط وقتی اضافه
 * شده که کد فعلی آن را رعایت می‌کند، پس هر شکست یک نقض تازه است.
 */
@AnalyzeClasses(packages = "com.example.roshdeandishe", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /** کنترلرها از طریق Facade یا سرویس کار می‌کنند، نه Entity مستقیم. */
    @ArchTest
    static final ArchRule controllersDoNotTouchEntities = noClasses()
            .that().areAnnotatedWith(Controller.class)
            .or().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    /** DTOهای request/response نباید به Entityهای JPA وابسته باشند. */
    @ArchTest
    static final ArchRule dtosDoNotTouchEntities = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);
}
