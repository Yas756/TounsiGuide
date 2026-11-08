package com.tounsiguide.identity.dto;

import com.tounsiguide.identity.domain.Role;
import com.tounsiguide.identity.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(String id, String email, String displayName, Set<Role> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                new TreeSet<>(user.getRoles()), user.getCreatedAt());
    }
}
