package io.github.mortogo321.recon.api.config;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The application's own {@link DataSource} — MySQL in a deployment, H2 locally.
 *
 * <p>Declared by hand rather than left to Boot for one reason: the moment a second
 * {@code DataSource} bean exists anywhere in the context (here, the read-only legacy Oracle pool),
 * {@code DataSourceAutoConfiguration} backs off completely and there is no primary data source at
 * all. Boot's documented answer to more than one data source is to own both and mark one
 * {@code @Primary}, which is what this does — while still binding to the standard
 * {@code spring.datasource.*} and {@code spring.datasource.hikari.*} keys, so nothing in the
 * configuration files becomes bespoke.
 *
 * <p>The transaction manager is here for the same reason: {@code legacyTransactionManager} is a
 * {@code PlatformTransactionManager} bean, so Boot's JPA one silently backs off and nothing is left
 * to commit the application's own writes. There is deliberately no XA manager spanning the two —
 * the batch job is idempotent and restartable instead, which is cheaper and easier to reason about
 * than a two-phase commit across a legacy Oracle we do not own.
 *
 * <p>The bean names matter: {@code dataSource} and {@code transactionManager} are what
 * {@code @EnableJdbcJobRepository} points the batch metadata at, and what Flyway, JPA and the
 * health contributors resolve as the single candidate.
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory, ObjectProvider<TransactionManagerCustomizers> customizers) {
        JpaTransactionManager manager = new JpaTransactionManager(entityManagerFactory);
        // Keeps spring.transaction.* honoured, which is what Boot's own bean would have applied.
        customizers.ifAvailable(customizer -> customizer.customize(manager));
        return manager;
    }
}
