package io.github.mortogo321.recon.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The module boundaries, enforced rather than described.
 *
 * <p>A README saying "the domain has no framework dependencies" survives exactly until the first
 * hurried change. These rules fail the build instead, which is the only version of an architectural
 * decision that still holds a year later.
 */
// Jars are deliberately included: the four library modules reach this module's test classpath as
// jars, and excluding them would leave every layer below `api` empty - a rule that passes because
// it saw nothing is worse than no rule at all.
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "io.github.mortogo321.recon";

    private static final String DOMAIN = ROOT + ".domain..";
    private static final String CORE = ROOT + ".core..";
    private static final String LEGACY = ROOT + ".legacy..";
    private static final String BATCH = ROOT + ".batch..";
    private static final String API = ROOT + ".api..";

    /**
     * The dependency direction the Gradle modules already imply. Stated here as well because Gradle
     * enforces it between modules, and this catches the day someone "temporarily" merges two.
     */
    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy(DOMAIN)
            .layer("Core")
            .definedBy(CORE)
            .layer("Legacy")
            .definedBy(LEGACY)
            .layer("Batch")
            .definedBy(BATCH)
            .layer("Api")
            .definedBy(API)
            .whereLayer("Api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Batch")
            .mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Core")
            .mayOnlyBeAccessedByLayers("Batch", "Api")
            .whereLayer("Legacy")
            .mayOnlyBeAccessedByLayers("Batch", "Api")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Core", "Legacy", "Batch", "Api");

    /**
     * The rule that gives the domain its value: the matching logic is testable in microseconds with
     * no context to start, and it cannot quietly acquire a database or an HTTP concern.
     */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "org.apache.ibatis..",
                    "com.fasterxml.jackson..",
                    "tools.jackson..");

    /** Money is never a {@code double}: 0.1 + 0.2 is not 0.3 and a reconciliation would say so. */
    @ArchTest
    static final ArchRule noFloatingPointMoney = fields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(DOMAIN, CORE, LEGACY, BATCH, API)
            .should()
            .notHaveRawType(double.class)
            .andShould()
            .notHaveRawType(float.class)
            .andShould()
            .notHaveRawType(Double.class)
            .andShould()
            .notHaveRawType(Float.class)
            .because("monetary amounts use BigDecimal through Money; binary floating point silently loses cents");

    /**
     * Persistence is an implementation detail of the service layer, not of a controller. Stated as
     * "no repository or entity manager", not "nothing from the repository package", because Spring
     * Data projections are declared as nested interfaces there and a controller naming one in a
     * response mapping is reading a view, not reaching into the database.
     */
    @ArchTest
    static final ArchRule controllersDoNotReachIntoPersistence = noClasses()
            .that()
            .resideInAPackage(ROOT + ".api.controller..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo("org.springframework.data.repository.Repository")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + ".legacy.mapper..", "jakarta.persistence..");

    /** MyBatis mappers are reached through the gateways that own the caching and the transactions. */
    @ArchTest
    static final ArchRule mappersAreUsedOnlyByGateways = noClasses()
            .that()
            .resideOutsideOfPackages(ROOT + ".legacy.gateway..", ROOT + ".legacy.mapper..", ROOT + ".legacy.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + ".legacy.mapper..");

    /**
     * Constructor injection only. Field injection hides a dependency from every constructor caller,
     * which is exactly the thing that makes a class untestable without a Spring context.
     */
    @ArchTest
    static final ArchRule noFieldInjection = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired");

    /** java.time or nothing: the legacy DATE columns are mapped, not passed through. */
    @ArchTest
    static final ArchRule noLegacyDateApi = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Date")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Calendar")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.sql.Timestamp");

    /** Anything worth printing is worth a logger and a level. */
    @ArchTest
    static final ArchRule noConsoleOutput =
            com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
