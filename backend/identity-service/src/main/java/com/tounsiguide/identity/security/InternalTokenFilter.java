package com.tounsiguide.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Protège les endpoints entre services ({@code /internal/**}, Sprint 3) : seuls la Gateway et les autres services
 * TounsiGuide connaissent le secret {@code tounsiguide.security.internal-token}, envoyé dans {@value #HEADER}.
 * La Gateway ne route jamais /internal/**. Les autres chemins gardent leur sécurité JWT habituelle.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] token;

    public InternalTokenFilter(@Value("${tounsiguide.security.internal-token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("tounsiguide.security.internal-token doit être renseigné");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (sent != null && MessageDigest.isEqual(token, sent.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        // Même forme que ApiError (AUTH_CONTRACT.md) ; le chemin ne contient que /internal/... (pas de guillemets).
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Jeton interne manquant ou invalide\","
                + "\"path\":\"" + request.getRequestURI().replace("\"", "").replace("\\", "") + "\"}");
    }
}
