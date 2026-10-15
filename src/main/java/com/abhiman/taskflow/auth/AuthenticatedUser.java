package com.abhiman.taskflow.auth;

import com.abhiman.taskflow.user.Role;

/**
 * The security principal for a request, built from the JWT's claims.
 * Controllers receive it with {@code @AuthenticationPrincipal AuthenticatedUser me}.
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean hasRole(Role other) {
        return role == other;
    }

    public boolean isManagerOrAdmin() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }
}
