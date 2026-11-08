package com.tounsiguide.identity.dto;

import com.tounsiguide.identity.domain.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty(message = "Au moins un rôle est requis") Set<Role> roles) {
}
