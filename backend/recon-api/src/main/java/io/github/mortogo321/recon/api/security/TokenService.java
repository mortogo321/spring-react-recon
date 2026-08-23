package io.github.mortogo321.recon.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues and validates demo credentials, returning a signed JWT the console stores in memory. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final DemoUserProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TokenService(
            JwtEncoder encoder, DemoUserProperties properties, PasswordEncoder passwordEncoder, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public IssuedToken authenticate(String username, String rawPassword) {
        DemoUserProperties.DemoUser user = properties.getUsers().get(username);
        // Always run the comparison, even for an unknown user, so a missing account and a wrong
        // password take the same amount of time and cannot be told apart from the outside.
        String stored = user == null ? "{noop}invalid" : user.getPassword();
        boolean valid = passwordEncoder.matches(Objects.toString(rawPassword, ""), stored) && user != null;
        if (!valid) {
            throw new InvalidCredentialsException();
        }
        return issue(username, user);
    }

    private IssuedToken issue(String username, DemoUserProperties.DemoUser user) {
        Instant now = Instant.now(clock);
        Duration ttl = Duration.ofMinutes(properties.getTokenTtlMinutes());
        List<String> roles = user.getRoles().stream()
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(username)
                .claim("name", user.getDisplayName() == null ? username : user.getDisplayName())
                .claim("roles", roles)
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws
                                .SignatureAlgorithm.RS256)
                        .build(), claims))
                .getTokenValue();
        return new IssuedToken(token, ttl.toSeconds(), username, claims.getClaim("name"), roles);
    }

    public record IssuedToken(String accessToken, long expiresInSeconds, String username, String displayName,
            List<String> roles) {}

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
