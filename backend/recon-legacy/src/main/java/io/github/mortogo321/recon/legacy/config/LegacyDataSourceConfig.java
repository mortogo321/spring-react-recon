package io.github.mortogo321.recon.legacy.config;

import java.util.Currency;

import javax.sql.DataSource;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.mortogo321.recon.domain.model.SettlementStatus;
import io.github.mortogo321.recon.legacy.typehandler.CurrencyTypeHandler;
import io.github.mortogo321.recon.legacy.typehandler.SettlementStatusTypeHandler;

/**
 * Second {@link DataSource} in the application, wired by hand because Boot's auto-configuration
 * only ever owns one. The pool is marked read-only: nothing in this codebase is allowed to write
 * to the legacy system, and enforcing it at the connection level beats hoping nobody adds an
 * {@code UPDATE} to a mapper later.
 */
@Configuration
@EnableConfigurationProperties(LegacyDataSourceProperties.class)
@MapperScan(
        basePackages = "io.github.mortogo321.recon.legacy.mapper",
        sqlSessionTemplateRef = "legacySqlSessionTemplate")
public class LegacyDataSourceConfig {

    static final String MAPPER_LOCATIONS = "classpath*:mybatis/*Mapper.xml";

    @Bean(destroyMethod = "close")
    public HikariDataSource legacyDataSource(LegacyDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("legacy-oracle");
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setDriverClassName(properties.driverClassName());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setConnectionTimeout(properties.connectionTimeout().toMillis());
        config.setReadOnly(true);
        config.setAutoCommit(true);
        if (properties.schema() != null && !properties.schema().isBlank()) {
            config.setSchema(properties.schema());
        }
        // Oracle round-trip reduction: prefetch rows and cache prepared statements per connection.
        config.addDataSourceProperty("defaultRowPrefetch", properties.fetchSize());
        config.addDataSourceProperty("implicitCachingEnabled", "true");
        return new HikariDataSource(config);
    }

    /**
     * Bootstraps the legacy schema when {@code recon.legacy.datasource.init-scripts} is set, which
     * is how the local H2-in-Oracle-mode profile stands in for a real Oracle instance. Against a
     * genuine Oracle the list is empty and this bean is inert — we never own that schema.
     */
    @Bean
    public DataSourceInitializer legacyDataSourceInitializer(
            @Qualifier("legacyDataSource") DataSource legacyDataSource, LegacyDataSourceProperties properties) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(legacyDataSource);
        initializer.setEnabled(!properties.initScripts().isEmpty());
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        properties.initScripts().forEach(location -> populator.addScript(resolver.getResource(location)));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    @Bean
    public SqlSessionFactory legacySqlSessionFactory(
            @Qualifier("legacyDataSource") DataSource legacyDataSource, LegacyDataSourceProperties properties)
            throws Exception {
        // Fully qualified: MyBatis' Configuration, not Spring's.
        org.apache.ibatis.session.Configuration mybatis = new org.apache.ibatis.session.Configuration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.setDefaultExecutorType(ExecutorType.REUSE);
        mybatis.setDefaultFetchSize(properties.fetchSize());
        mybatis.setDefaultStatementTimeout(120);
        mybatis.setCacheEnabled(false); // reconciliation must always see the live feed
        mybatis.setLazyLoadingEnabled(false);
        mybatis.setCallSettersOnNulls(true);

        TypeHandlerRegistry handlers = mybatis.getTypeHandlerRegistry();
        handlers.register(Currency.class, new CurrencyTypeHandler());
        handlers.register(SettlementStatus.class, new SettlementStatusTypeHandler());

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(legacyDataSource);
        factory.setConfiguration(mybatis);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATIONS));
        factory.setTypeAliasesPackage("io.github.mortogo321.recon.legacy.dto");
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate legacySqlSessionTemplate(SqlSessionFactory legacySqlSessionFactory) {
        return new SqlSessionTemplate(legacySqlSessionFactory, ExecutorType.REUSE);
    }

    /**
     * Read-only transaction manager for the legacy source. Note there is deliberately no
     * distributed transaction across Oracle and MySQL — the batch job is designed to be
     * idempotent and restartable instead, which is cheaper and far easier to reason about.
     */
    @Bean
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyDataSource") DataSource legacyDataSource, LegacyDataSourceProperties properties) {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(legacyDataSource);
        manager.setEnforceReadOnly(properties.enforceReadOnly());
        return manager;
    }
}
