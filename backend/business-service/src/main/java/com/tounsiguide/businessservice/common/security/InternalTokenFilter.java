package com.tounsiguide.businessservice.common.security;

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
import java.util.Set;

/**
 * Only the Gateway and the other TounsiGuide services may call this service. They send the shared secret
 * {@code tounsiguide.security.internal-token} in {@value #HEADER}; any other request answers 401 before reaching a
 * controller, so nobody can call 8084 directly with forged X-User-* headers. Health and info stay open for monitoring.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private static final Set<String> OPEN_PATHS = Set.of("/actuator/health", "/actuator/info");

    private final byte[] token;

    public InternalTokenFilter(@Value("${tounsiguide.security.internal-token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("tounsiguide.security.internal-token must be set");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return OPEN_PATHS.contains(path) || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isValid(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal token\","
                + "\"path\":\"" + json(request.getRequestURI()) + "\",\"details\":{}}");
    }

    /** Constant-time comparison, so the time taken does not tell how much of the token was right. */
    private boolean isValid(String sent) {
        return sent != null && MessageDigest.isEqual(token, sent.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> out.append(c < 0x20 ? ' ' : c);
            }
        }
        return out.toString();
    }
}
