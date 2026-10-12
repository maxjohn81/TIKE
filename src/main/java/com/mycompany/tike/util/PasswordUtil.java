package com.mycompany.tike.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide.");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, hashedPassword);
    }
}
