package com.tounsiguide.identity.controller;

import com.tounsiguide.identity.dto.PageResponse;
import com.tounsiguide.identity.dto.UpdateRolesRequest;
import com.tounsiguide.identity.dto.UserResponse;
import com.tounsiguide.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return userService.list(PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending()));
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@PathVariable String id, @Valid @RequestBody UpdateRolesRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return userService.updateRoles(jwt.getSubject(), id, request.roles());
    }
}
