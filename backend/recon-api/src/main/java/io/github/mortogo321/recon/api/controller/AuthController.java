package io.github.mortogo321.recon.api.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mortogo321.recon.api.dto.AuthDtos;
import io.github.mortogo321.recon.api.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange demo credentials for a bearer token")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        TokenService.IssuedToken issued = tokenService.authenticate(request.username(), request.password());
        return new AuthDtos.LoginResponse(
                issued.accessToken(),
                "Bearer",
                issued.expiresInSeconds(),
                new AuthDtos.UserInfo(issued.username(), issued.displayName(), issued.roles()));
    }

    /** Lets the console rehydrate identity and role-gated UI from a stored token on reload. */
    @GetMapping("/me")
    @Operation(summary = "Describe the caller behind the presented token")
    public ResponseEntity<AuthDtos.UserInfo> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return ResponseEntity.ok(new AuthDtos.UserInfo(
                jwt.getSubject(), jwt.getClaimAsString("name"), roles == null ? List.of() : roles));
    }
}
