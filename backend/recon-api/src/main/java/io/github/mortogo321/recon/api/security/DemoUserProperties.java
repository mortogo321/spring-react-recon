package io.github.mortogo321.recon.api.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo credentials, externalised so nothing is hard-coded in source and every value can be
 * overridden by an environment variable. A real deployment replaces this whole mechanism with the
 * organisation's identity provider — the resource-server configuration would not change.
 */
@ConfigurationProperties(prefix = "recon.auth")
public class DemoUserProperties {

    private String issuer = "recon-console";
    private long tokenTtlMinutes = 60;
    private Map<String, DemoUser> users = new LinkedHashMap<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public void setTokenTtlMinutes(long tokenTtlMinutes) {
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public Map<String, DemoUser> getUsers() {
        return users;
    }

    public void setUsers(Map<String, DemoUser> users) {
        this.users = users;
    }

    public static class DemoUser {
        private String password;
        private String displayName;
        private List<String> roles = List.of();

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
