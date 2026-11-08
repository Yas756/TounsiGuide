package com.tounsiguide.identity.service;

import com.tounsiguide.identity.domain.RefreshToken;
import com.tounsiguide.identity.domain.Role;
import com.tounsiguide.identity.domain.User;
import com.tounsiguide.identity.dto.AuthResponse;
import com.tounsiguide.identity.dto.LoginRequest;
import com.tounsiguide.identity.dto.RefreshRequest;
import com.tounsiguide.identity.dto.RegisterRequest;
import com.tounsiguide.identity.dto.UserResponse;
import com.tounsiguide.identity.exception.ApiException;
import com.tounsiguide.identity.repository.RefreshTokenRepository;
import com.tounsiguide.identity.repository.UserRepository;
import com.tounsiguide.identity.security.JwtService;
import com.tounsiguide.identity.security.TokenUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Email ou mot de passe incorrect";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email");
        }
        // Toute inscription publique donne uniquement le rôle MEMBER : ADMIN s'attribue via /admin/users.
        User user = new User(email, request.displayName().trim(),
                passwordEncoder.encode(request.password()), EnumSet.of(Role.MEMBER));
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> ApiException.unauthorized(BAD_CREDENTIALS));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        return issueTokens(user);
    }

    /**
     * Rotation : l'ancien refresh token est révoqué à chaque usage. Si un token déjà révoqué est présenté
     * (rejeu probable), tous les refresh tokens de l'utilisateur sont révoqués. noRollbackFor garantit que
     * cette révocation est bien enregistrée malgré l'exception renvoyée au client.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(request.refreshToken()))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token invalide"));

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw ApiException.unauthorized("Refresh token invalide");
        }
        if (stored.isExpired()) {
            throw ApiException.unauthorized("Refresh token expiré");
        }
        User user = userRepository.findById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token invalide"));

        stored.setRevoked(true);
        return issueTokens(user);
    }

    /** Idempotent : un token inconnu ou déjà révoqué ne provoque pas d'erreur. */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(request.refreshToken()))
                .ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = TokenUtils.generateOpaqueToken();
        refreshTokenRepository.save(new RefreshToken(user.getId(), TokenUtils.sha256Hex(rawRefresh),
                Instant.now().plus(jwtService.refreshTokenTtl())));
        return new AuthResponse(accessToken, rawRefresh, "Bearer", jwtService.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
