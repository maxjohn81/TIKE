package com.mycompany.tike.model;

import java.util.Objects;

public final class UserCredential {
    private final User user;
    private final String passwordHash;

    public UserCredential(User user, String passwordHash) {
        this.user = Objects.requireNonNull(user, "user");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public User getUser() {
        return user;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
