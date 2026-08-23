package io.github.mortogo321.recon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Reconciliation console API — the only deployable module.
 *
 * <p>It lives in the root {@code io.github.mortogo321.recon} package deliberately. Boot derives
 * both its component scan and its entity scan from the package of this class, so rooting it here is
 * what lets the four library modules below it be discovered without a list of scan paths that would
 * need editing every time a module is added.
 *
 * <p>{@code @EnableJpaRepositories} is still pinned explicitly: repository scanning must never
 * wander into the legacy MyBatis mappers, which are interfaces in the same tree and would be a
 * confusing failure if Spring Data tried to implement them.
 */
@SpringBootApplication
@EnableJpaRepositories("io.github.mortogo321.recon.core.repository")
public class ReconApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconApiApplication.class, args);
    }
}
