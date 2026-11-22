package com.tounsiguide.identity.controller;

import com.tounsiguide.identity.domain.Role;
import com.tounsiguide.identity.dto.UserResponse;
import com.tounsiguide.identity.service.UserService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints appelés par les autres services (jamais par le navigateur : la Gateway ne route pas /internal/**).
 * Protégés par {@link com.tounsiguide.identity.security.InternalTokenFilter}.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Business Service (S3-B06) : ajoute BUSINESS_REP quand une demande de page est approuvée. Les autres rôles
     * sont conservés ; appeler deux fois ne change rien. 404 si l'utilisateur n'existe pas. Seul ce rôle peut
     * être ajouté ici (ADMIN reste attribué par un administrateur via /api/v1/admin/users/{id}/roles).
     */
    @PostMapping("/{id}/roles/BUSINESS_REP")
    public UserResponse grantBusinessRep(@PathVariable String id) {
        return userService.addRole(id, Role.BUSINESS_REP);
    }
}
