package com.abhiman.taskflow.user;

import java.time.Instant;

/** Public view of a user. Never expose the entity itself: it contains the password hash. */
public record UserResponse(Long id, String fullName, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
