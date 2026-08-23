package io.github.mortogo321.recon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import io.github.mortogo321.recon.batch.service.ReconJobOperations;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.entity.RunStatus;
import io.github.mortogo321.recon.core.repository.ReconExceptionRepository;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.domain.match.MatchStatus;

/**
 * The same reconciliation as {@code ReconciliationJobIT}, against the two real engines.
 *
 * <p>Everywhere else the legacy side is H2 in Oracle compatibility mode, which is fast, honest about
 * the dialect subset the mappers stay inside — and unable to prove three things that only a real
 * Oracle can: that the hand-written SQL in the MyBatis XML actually parses on Oracle, that the
 * type handlers read Oracle's own {@code NUMBER} and {@code DATE} representations correctly, and
 * that the read-only account the API connects as can in fact see the owner's tables through the
 * synonyms it is granted. On the MySQL side it proves the Flyway migrations run on MySQL rather
 * than on H2's approximation of it.
 *
 * <p>Oracle is seeded by the very script {@code docker-compose.yml} mounts, so the account split
 * under test here — an owner that cannot log in, an application user with SELECT and nothing else —
 * is the arrangement that ships, not a test fixture that resembles it.
 *
 * <p>Tagged {@code docker} and therefore excluded from {@code test}; run it with
 * {@code ./gradlew :backend:recon-api:integrationTest}. Oracle Free takes a couple of minutes to
 * come up the first time, which is exactly why this is not in the default build.
 */
@Tag("docker")
@Testcontainers
// The default mock web environment, not NONE: the security filter chain is part of the context and
// needs an HttpSecurity to build against.
@SpringBootTest(properties = {"recon.outbox.dispatch-interval=PT1H"})
@ActiveProfiles("docker")
class RealDatabasesIT {

    private static final LocalDate DEMO_DAY = LocalDate.of(2026, 8, 20);
    private static final String APP_USER = "recon_ro";
    private static final String OWNER = "RECON_LEGACY";

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "STOPPED", "ABANDONED");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:9"))
            .withDatabaseName("recon")
            .withUsername("recon")
            .withPassword("recon");

    /**
     * The image's own init hook, given the same three files compose mounts. The script creates the
     * schema owner, applies the legacy DDL and demo feed, then grants SELECT and the synonyms —
     * and the image only reports ready once it has finished, so no extra wait strategy is needed.
     */
    @Container
    static final OracleContainer ORACLE = new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim"))
            .withEnv("ORACLE_APP_USER", APP_USER)
            .withEnv("ORACLE_APP_PASSWORD", APP_USER)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(repoFile("docker/oracle/01-init.sh"), 0555),
                    "/container-entrypoint-initdb.d/01-init.sh")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/legacy/oracle-schema.sql"),
                    "/legacy-sql/oracle-schema.sql")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/legacy/oracle-demo-data.sql"),
                    "/legacy-sql/oracle-demo-data.sql")
            .withStartupTimeout(Duration.ofMinutes(10));

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        // The docker profile reads every one of these from the environment with a compose-shaped
        // default, so pointing it at a container is a matter of overriding host and port only.
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("recon.legacy.datasource.url", RealDatabasesIT::legacyUrl);
        registry.add("recon.legacy.datasource.username", () -> APP_USER);
        registry.add("recon.legacy.datasource.password", () -> APP_USER);
        registry.add("recon.legacy.datasource.schema", () -> OWNER);
    }

    @Autowired
    private ReconJobOperations jobs;

    @Autowired
    private ReconRunService runs;

    @Autowired
    private ReconExceptionRepository exceptions;

    @Test
    @DisplayName("the demo day reconciles to the same figures on real Oracle and real MySQL")
    void reconcilesTheDemoDayOnRealEngines() {
        ReconRunEntity run = runToCompletion(DEMO_DAY, "default");

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED_WITH_BREAKS);
        assertThat(run.getSettlementRows()).isEqualTo(100);
        assertThat(run.getLedgerRows()).isEqualTo(94);
        assertThat(run.getExcludedRows()).isEqualTo(4);
        assertThat(run.getExceptionKeys()).isEqualTo(13);
        assertThat(run.getMatchRate()).isEqualByComparingTo(new BigDecimal("86.73"));
        assertThat(run.getExposure().amount()).isEqualByComparingTo(new BigDecimal("29115.80"));

        // Identical to the H2 run, down to the class of every break. Any divergence here is a
        // dialect or type-handler difference the compatibility mode was hiding.
        assertThat(breakdownOf(run))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        MatchStatus.AMOUNT_MISMATCH, 3L,
                        MatchStatus.MISSING_IN_LEDGER, 4L,
                        MatchStatus.MISSING_IN_SETTLEMENT, 3L,
                        MatchStatus.DUPLICATE_SETTLEMENT, 1L,
                        MatchStatus.CURRENCY_MISMATCH, 2L));
    }

    @Test
    @DisplayName("the account the API holds on the legacy system can read it and cannot change it")
    void theLegacyAccountIsReadOnly() throws Exception {
        // The control the whole exercise rests on: reconciliation reads someone else's system of
        // record, and if the credentials it holds can change that record it is not a control any
        // more. H2 cannot test this at all — the grant model is the part being asserted.
        try (Connection connection = legacyConnection();
                Statement statement = connection.createStatement()) {

            ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + OWNER + ".STG_SETTLEMENT_TXN");
            rows.next();
            assertThat(rows.getInt(1)).isPositive();
        }

        assertThatThrownBy(() -> {
                    try (Connection connection = legacyConnection();
                            Statement statement = connection.createStatement()) {
                        statement.executeUpdate("DELETE FROM " + OWNER + ".STG_SETTLEMENT_TXN");
                    }
                })
                .isInstanceOf(SQLException.class)
                // Oracle 23 answers ORA-41900 "missing DELETE privilege"; older releases said
                // ORA-01031 "insufficient privileges". Asserting on the word rather than the code
                // keeps this true across both without weakening what is being proved.
                .hasMessageContaining("privilege");
    }

    private static Connection legacyConnection() throws SQLException {
        return DriverManager.getConnection(legacyUrl(), APP_USER, APP_USER);
    }

    private static String legacyUrl() {
        return "jdbc:oracle:thin:@//%s:%d/%s".formatted(ORACLE.getHost(), ORACLE.getOraclePort(), ORACLE.getDatabaseName());
    }

    private ReconRunEntity runToCompletion(LocalDate businessDate, String profile) {
        runs.openRun(businessDate, profile);
        long executionId = jobs.launch(businessDate, profile).executionId();

        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(deadline)) {
            String status = jobs.findExecution(executionId)
                    .map(ReconJobOperations.JobHandle::status)
                    .orElse("UNKNOWN");
            if (TERMINAL.contains(status)) {
                return runs.findByKey(businessDate, profile).orElseThrow();
            }
            sleep();
        }
        throw new AssertionError("Execution " + executionId + " did not finish in three minutes");
    }

    private Map<MatchStatus, Long> breakdownOf(ReconRunEntity run) {
        Map<MatchStatus, Long> counts = new EnumMap<>(MatchStatus.class);
        exceptions.breakdownByRun(run.getId()).forEach(row -> counts.merge(row.getStatus(), row.getTotal(), Long::sum));
        return counts;
    }

    private static void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Tests run with the module directory as their working directory, so a repo-relative file has
     * to be found rather than assumed: walk up until the settings script that defines the build.
     */
    private static Path repoFile(String relative) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("settings.gradle.kts"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Could not locate the repository root from " + Path.of("").toAbsolutePath());
        }
        return directory.resolve(relative);
    }
}
