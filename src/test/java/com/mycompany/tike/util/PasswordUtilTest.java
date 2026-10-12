package com.mycompany.tike.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    @Test
    void shouldHashAndValidatePassword() {
        String password = "Secret123!";
        String hashed = PasswordUtil.hash(password);

        assertNotNull(hashed);
        assertTrue(hashed.startsWith("$2a$") || hashed.startsWith("$2b$"));
        assertTrue(PasswordUtil.matches(password, hashed));
        assertFalse(PasswordUtil.matches("WrongPassword", hashed));
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(" "));
    }
}
