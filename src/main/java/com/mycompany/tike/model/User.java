package com.mycompany.tike.model;

import java.util.Objects;

public final class User {
    private final long id;
    private final String fullName;
    private final String username;
    private final UserRole role;
    private final boolean active;

    public User(long id, String fullName, String username, UserRole role, boolean active) {
        if (id < 0) {
            throw new IllegalArgumentException("L'identifiant utilisateur est invalide.");
        }
        this.id = id;
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.username = Objects.requireNonNull(username, "username");
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
