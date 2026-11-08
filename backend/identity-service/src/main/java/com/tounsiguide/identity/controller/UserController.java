package com.tounsiguide.identity.controller;

import com.tounsiguide.identity.dto.UserResponse;
import com.tounsiguide.identity.dto.UserSummary;
import com.tounsiguide.identity.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getMe(jwt.getSubject());
    }

    /** Profil minimal (id + nom affiché) pour tout utilisateur authentifié. */
    @GetMapping("/{id}")
    public UserSummary summary(@PathVariable String id) {
        return userService.getSummary(id);
    }
}
