package io.github.mortogo321.recon.api.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Enforces {@code Idempotency-Key} on the state-changing endpoints that are expensive to repeat.
 *
 * <p>The concrete problem: an operator clicks "run reconciliation", the response is slow, they click
 * again. Without this, the second click races the first through the launch path. The key is held for
 * a short window and a replay is answered with 409 rather than being silently swallowed — the client
 * needs to know its request was not a fresh one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> GUARDED_PREFIXES = Set.of("/api/runs");

    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return GUARDED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            // Absent key is allowed: the endpoint itself is idempotent by business key. The header
            // only buys protection against the double-click, so it is advisory, not mandatory.
            chain.doFilter(request, response);
            return;
        }
        String scoped = request.getRequestURI() + "|" + key;
        if (seen.asMap().putIfAbsent(scoped, Boolean.TRUE) != null) {
            log.info("Rejected replay of {} with idempotency key {}", request.getRequestURI(), key);
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    """
                    {"type":"about:blank","title":"Duplicate request",\
                    "status":409,"detail":"This Idempotency-Key has already been used."}""");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Only a request that was actually processed consumes the key. Claiming it up front is
            // what makes the double-click safe, but holding it after a 401 or a validation failure
            // would lock the operator out of retrying the same click for the whole window.
            if (response.getStatus() >= HttpStatus.BAD_REQUEST.value()) {
                seen.invalidate(scoped);
            }
        }
    }
}
