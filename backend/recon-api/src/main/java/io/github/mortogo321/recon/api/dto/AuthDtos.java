package io.github.mortogo321.recon.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {}

    public record UserInfo(String username, String displayName, List<String> roles) {}
}
