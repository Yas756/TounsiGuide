package com.tounsiguide.locationservice.common.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Identity of the caller, read from the headers the Gateway adds after it has validated the JWT
 * (see AUTH_CONTRACT.md). This service is never exposed directly: only the Gateway may reach it.
 */
public record CurrentUser(String id, String displayName, Set<String> roles) {

    public static final String ID_HEADER = "X-User-Id";
    public static final String NAME_HEADER = "X-User-Name";
    public static final String ROLES_HEADER = "X-User-Roles";

    private static final String ADMIN = "ADMIN";
    private static final String DEFAULT_NAME = "Membre";

    /**
     * @param id          value of X-User-Id (the user's UUID)
     * @param encodedName value of X-User-Name, URL-encoded UTF-8 (names can contain accents or Arabic)
     * @param roles       value of X-User-Roles, comma separated
     * @throws UnauthorizedException when there is no user id
     */
    public static CurrentUser fromHeaders(String id, String encodedName, String roles) {
        if (id == null || id.isBlank()) {
            throw new UnauthorizedException("Authentication is required");
        }
        String name = DEFAULT_NAME;
        if (encodedName != null && !encodedName.isBlank()) {
            name = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        }
        Set<String> roleSet = roles == null ? Set.of() : Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(id.trim(), name, roleSet);
    }

    public boolean isAdmin() {
        return roles.contains(ADMIN);
    }

    /** Only an administrator may change the Location directory. */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Only an administrator can change the Location directory");
        }
    }
}
