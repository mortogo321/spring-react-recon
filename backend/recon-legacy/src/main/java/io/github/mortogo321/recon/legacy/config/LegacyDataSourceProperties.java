package io.github.mortogo321.recon.legacy.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the legacy Oracle instance. Kept separate from Spring Boot's
 * {@code spring.datasource.*} because that one belongs to the application's own MySQL schema.
 */
@Validated
@ConfigurationProperties(prefix = "recon.legacy.datasource")
public record LegacyDataSourceProperties(
        @NotBlank String url,
        @NotBlank String username,
        String password,
        String driverClassName,
        @Positive int maximumPoolSize,
        Duration connectionTimeout,
        String schema,
        /** SQL scripts to run against this data source on startup. Empty against a real Oracle. */
        java.util.List<String> initScripts,
        /**
         * Whether the transaction manager also issues {@code SET TRANSACTION READ ONLY}. True
         * against Oracle, where it is a second line of defence behind the read-only pool. H2 has no
         * such statement, so the local profile turns it off and relies on the pool alone.
         */
        Boolean enforceReadOnly,
        // fetchSize: Oracle's default of 10 rows per round trip makes a multi-million row extract
        // painfully chatty. 1000 is a sane batch-friendly default.
        @Positive int fetchSize) {

    public LegacyDataSourceProperties {
        driverClassName = driverClassName == null || driverClassName.isBlank()
                ? "oracle.jdbc.OracleDriver"
                : driverClassName;
        maximumPoolSize = maximumPoolSize <= 0 ? 8 : maximumPoolSize;
        connectionTimeout = connectionTimeout == null ? Duration.ofSeconds(10) : connectionTimeout;
        fetchSize = fetchSize <= 0 ? 1000 : fetchSize;
        password = password == null ? "" : password;
        enforceReadOnly = enforceReadOnly == null || enforceReadOnly;
        initScripts = initScripts == null ? java.util.List.of() : java.util.List.copyOf(initScripts);
    }
}
