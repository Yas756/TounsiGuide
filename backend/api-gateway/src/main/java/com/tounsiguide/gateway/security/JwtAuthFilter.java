package com.tounsiguide.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates the access token (HS256 JWT issued by the Identity Service) and tells the services behind the
 * Gateway who is calling, through X-User-Id, X-User-Name and X-User-Roles (see AUTH_CONTRACT.md).
 *
 * <ul>
 *   <li>Any X-User-* header sent by the client is removed first, so nobody can pretend to be someone else.</li>
 *   <li>/api/v1/auth/**, CORS preflight and /actuator/** need no token.</li>
 *   <li>Reading (GET/HEAD on /api/v1/**) is public, except account data (/api/v1/users/**, /api/v1/admin/**).
 *       A token that is sent must still be valid.</li>
 *   <li>Everything else needs a valid token, otherwise the answer is 401.</li>
 * </ul>
 * The services still decide what the user may do (author only, admin only...).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter implements Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_NAME_HEADER = "X-User-Name";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final String USER_HEADER_PREFIX = "x-user-";

    private final byte[] secret;
    private final String issuer;

    public JwtAuthFilter(
            @Value("${tounsiguide.security.jwt.secret}") String secret,
            @Value("${tounsiguide.security.jwt.issuer:tounsiguide-identity}") String issuer
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("tounsiguide.security.jwt.secret must be at least 32 characters");
        }
        this.issuer = issuer;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(method) || path.startsWith("/api/v1/auth/") || path.startsWith("/actuator")) {
            chain.doFilter(new UserHeadersRequest(request, Map.of()), response);
            return;
        }

        String token = bearerToken(request);
        if (token == null) {
            if (isPublicRead(method, path)) {
                chain.doFilter(new UserHeadersRequest(request, Map.of()), response);
            } else {
                unauthorized(request, response, "Authentication is required");
            }
            return;
        }

        Map<String, String> identity;
        try {
            identity = verify(token);
        } catch (InvalidTokenException exception) {
            unauthorized(request, response, exception.getMessage());
            return;
        }
        chain.doFilter(new UserHeadersRequest(request, identity), response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static boolean isPublicRead(String method, String path) {
        boolean safeMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        // Account data is never public, even for reading.
        boolean accountData = path.startsWith("/api/v1/users/") || path.startsWith("/api/v1/admin/");
        return safeMethod && path.startsWith("/api/v1/") && !accountData;
    }

    private Map<String, String> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                throw new InvalidTokenException("Invalid token");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw new InvalidTokenException("Invalid token");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
                throw new InvalidTokenException("Token expired");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new InvalidTokenException("Invalid token");
            }

            List<String> roles = claims.getStringListClaim("roles");
            String name = claims.getStringClaim("name");

            Map<String, String> identity = new LinkedHashMap<>();
            identity.put(USER_ID_HEADER, subject);
            identity.put(USER_ROLES_HEADER, roles == null ? "" : String.join(",", roles));
            if (name != null && !name.isBlank()) {
                // Names can hold accents or Arabic letters, which are not safe in a raw HTTP header.
                identity.put(USER_NAME_HEADER, URLEncoder.encode(name, StandardCharsets.UTF_8));
            }
            return identity;
        } catch (ParseException | JOSEException exception) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    private static void unauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String message
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"" + json(message)
                + "\",\"path\":\"" + json(request.getRequestURI()) + "\",\"details\":{}}");
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

    private static final class InvalidTokenException extends RuntimeException {
        InvalidTokenException(String message) {
            super(message);
        }
    }

    /** The request as the services will see it: client X-User-* headers removed, trusted ones added. */
    private static final class UserHeadersRequest extends HttpServletRequestWrapper {

        private final Map<String, String> userHeaders = new LinkedHashMap<>();

        UserHeadersRequest(HttpServletRequest request, Map<String, String> identity) {
            super(request);
            identity.forEach((name, value) -> userHeaders.put(name.toLowerCase(Locale.ROOT), value));
        }

        private static boolean isUserHeader(String name) {
            return name != null && name.toLowerCase(Locale.ROOT).startsWith(USER_HEADER_PREFIX);
        }

        @Override
        public String getHeader(String name) {
            if (isUserHeader(name)) {
                return userHeaders.get(name.toLowerCase(Locale.ROOT));
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isUserHeader(name)) {
                String value = userHeaders.get(name.toLowerCase(Locale.ROOT));
                return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Enumeration<String> original = super.getHeaderNames(); original != null && original.hasMoreElements(); ) {
                String name = original.nextElement();
                if (!isUserHeader(name)) {
                    names.add(name);
                }
            }
            userHeaders.keySet().forEach(name -> names.add(canonical(name)));
            return Collections.enumeration(names);
        }

        private static String canonical(String lowerCaseName) {
            return switch (lowerCaseName) {
                case "x-user-id" -> USER_ID_HEADER;
                case "x-user-name" -> USER_NAME_HEADER;
                case "x-user-roles" -> USER_ROLES_HEADER;
                default -> lowerCaseName;
            };
        }
    }
}
