package io.github.mortogo321.recon.core.config;

/**
 * Who is acting right now, for audit columns. Declared here so the persistence layer does not
 * depend on Spring Security; the API module supplies the real implementation.
 */
public interface CurrentActorProvider {

    String SYSTEM = "system";

    String currentActor();
}
