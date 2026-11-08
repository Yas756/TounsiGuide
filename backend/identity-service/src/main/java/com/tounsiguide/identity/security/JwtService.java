package com.tounsiguide.identity.security;

import com.tounsiguide.identity.config.SecurityProperties;
import com.tounsiguide.identity.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Émet les access tokens JWT (HS256). Claims : sub = id utilisateur, email, name, roles (liste de chaînes).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties.Jwt props;

    public JwtService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.props = properties.jwt();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration accessTokenTtl() {
        return props.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return props.refreshTokenTtl();
    }
}
