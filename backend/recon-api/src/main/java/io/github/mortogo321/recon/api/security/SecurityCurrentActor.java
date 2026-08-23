package io.github.mortogo321.recon.api.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.github.mortogo321.recon.core.config.CurrentActorProvider;

/**
 * Supplies the authenticated subject for JPA audit columns. Falls back to {@code system} for
 * anything the scheduler or the batch runs unattended, so audit columns are never null.
 */
@Component
public class SecurityCurrentActor implements CurrentActorProvider {

    @Override
    public String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? SYSTEM : name;
    }
}
