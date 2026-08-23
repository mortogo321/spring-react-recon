package io.github.mortogo321.recon.core.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditDateTimeProvider")
@EnableConfigurationProperties(ToleranceProperties.class)
public class JpaAuditingConfig {

    /** Injectable clock so time-dependent behaviour is testable without sleeping. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentActorProvider currentActorProvider() {
        return () -> CurrentActorProvider.SYSTEM;
    }

    @Bean
    public AuditorAware<String> auditorAware(CurrentActorProvider provider) {
        return () -> Optional.ofNullable(provider.currentActor()).or(() -> Optional.of(CurrentActorProvider.SYSTEM));
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
