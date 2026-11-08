package com.tounsiguide.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tounsiguide.security")
public record SecurityProperties(Jwt jwt, BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record BootstrapAdmin(String email, String password) {
    }
}
