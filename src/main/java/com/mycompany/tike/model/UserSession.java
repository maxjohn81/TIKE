package com.mycompany.tike.model;

import java.util.Objects;

public final class UserSession {
    private final long userId;
    private final String displayName;
    private final String username;
    private final UserRole role;

    private UserSession(long userId, String displayName, String username, UserRole role) {
        this.userId = userId;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.username = Objects.requireNonNull(username, "username");
        this.role = Objects.requireNonNull(role, "role");
    }

    public static UserSession authenticated(User user) {
        Objects.requireNonNull(user, "user");
        if (!user.isActive()) {
            throw new IllegalArgumentException("Un utilisateur désactivé ne peut pas ouvrir une session.");
        }
        return new UserSession(user.getId(), user.getFullName(), user.getUsername(), user.getRole());
    }

    public long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }
}
