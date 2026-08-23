package io.github.mortogo321.recon.core.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.github.mortogo321.recon.core.config.ToleranceProperties;
import io.github.mortogo321.recon.domain.match.ToleranceRule;
import io.github.mortogo321.recon.domain.money.Money;

/** Resolves a profile name to the immutable domain rule the engine actually uses. */
@Service
public class ToleranceProfileRegistry {

    public static final String DEFAULT_PROFILE = "default";

    private final ToleranceProperties properties;

    public ToleranceProfileRegistry(ToleranceProperties properties) {
        this.properties = properties;
    }

    /**
     * The profile a request actually runs under. Callers normalise once through here rather than
     * each deciding what an absent profile means — a run row and its job parameters disagreeing on
     * that is how you end up with two records for the same reconciliation.
     */
    public String effectiveProfile(String requested) {
        return requested == null || requested.isBlank() ? DEFAULT_PROFILE : requested;
    }

    public ToleranceRule resolve(String profileName) {
        String name = effectiveProfile(profileName);
        Map<String, ToleranceProperties.Profile> profiles = properties.getProfiles();
        ToleranceProperties.Profile profile = profiles.get(name);
        if (profile == null) {
            throw new UnknownToleranceProfileException(name, profiles.keySet());
        }
        return new ToleranceRule(Money.of(profile.getAbsolute(), profile.getCurrency()), profile.getBps());
    }

    public Set<String> availableProfiles() {
        return properties.getProfiles().keySet();
    }

    public static final class UnknownToleranceProfileException extends IllegalArgumentException {
        public UnknownToleranceProfileException(String requested, Set<String> known) {
            super("Unknown tolerance profile '" + requested + "'; configured profiles: " + known);
        }
    }
}
