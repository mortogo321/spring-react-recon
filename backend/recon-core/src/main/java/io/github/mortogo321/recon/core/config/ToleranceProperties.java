package io.github.mortogo321.recon.core.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Named tolerance profiles, configured rather than compiled in. Business tolerances change without
 * a release — a card scheme adjusts its rounding and someone needs to widen the allowance today.
 */
@Validated
@ConfigurationProperties(prefix = "recon.tolerance")
public class ToleranceProperties {

    /** Profile name -> settings. The {@code default} entry is used when none is requested. */
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, Profile> profiles) {
        this.profiles = profiles;
    }

    public static class Profile {

        @NotBlank
        private String currency = "THB";

        @Min(0)
        private BigDecimal absolute = BigDecimal.ZERO;

        @Min(0)
        private int bps;

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public BigDecimal getAbsolute() {
            return absolute;
        }

        public void setAbsolute(BigDecimal absolute) {
            this.absolute = absolute;
        }

        public int getBps() {
            return bps;
        }

        public void setBps(int bps) {
            this.bps = bps;
        }
    }
}
