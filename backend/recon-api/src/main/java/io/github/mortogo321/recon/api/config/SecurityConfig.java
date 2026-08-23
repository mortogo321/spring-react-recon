package io.github.mortogo321.recon.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import io.github.mortogo321.recon.api.security.DemoUserProperties;
import io.github.mortogo321.recon.api.security.JwtKeys;

/**
 * Stateless JWT resource server.
 *
 * <p>Authorisation is enforced twice on purpose: coarsely here by URL, and precisely at the service
 * layer with {@code @PreAuthorize}. The URL rules are the thing an auditor can read at a glance;
 * the method rules are the thing that still holds when someone adds a second controller onto the
 * same service next quarter.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(DemoUserProperties.class)
public class SecurityConfig {

    @Bean
    public JwtKeys jwtKeys() {
        return JwtKeys.generate();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtKeys keys) {
        RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID("recon-demo")
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtKeys keys) {
        return NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating encoder: the {noop} demo passwords in application.yml stay readable while any
        // real deployment can drop in {bcrypt} hashes without a code change.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Maps the token's {@code roles} claim onto Spring authorities with the ROLE_ prefix. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter converter,
            // Qualified: Spring MVC contributes a CorsConfigurationSource of its own.
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {
        return http.csrf(csrf -> csrf.disable()) // stateless bearer-token API; no cookies to forge
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Everything operational stays behind a role, including the metrics scrape.
                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${recon.security.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        // The Vite dev server and the preview server the e2e suite drives. A deployed console is
        // served same-origin behind the reverse proxy and needs none of this, which is why the
        // list is a property: the deployment overrides it with its own origin, or with nothing.
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Only what is actually sent. ETag and X-Total-Count were listed here once and never
        // emitted by anything: a browser cannot read a header the server does not set, so
        // advertising them only misleads whoever reads this next. Paged reads carry their
        // totals in the body (totalElements/totalPages), which is what the console uses.
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
