package com.mycompany.tike.service;

import com.mycompany.tike.dao.UserDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserCredential;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.util.PasswordUtil;
import java.sql.SQLException;
import java.util.Optional;

public final class AuthenticationService {
    private final UserDao userDao;

    public AuthenticationService(UserDao userDao) {
        this.userDao = userDao;
    }

    public UserSession authenticate(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return null;
        }
        Optional<UserCredential> credential = userDao.findActiveByUsername(username.trim());
        if (credential.isEmpty()) {
            return null;
        }
        String passwordText = new String(password);
        boolean matches;
        try {
            matches = PasswordUtil.matches(passwordText, credential.get().getPasswordHash());
        } catch (IllegalArgumentException e) {
            matches = false;
        }
        return matches ? UserSession.authenticated(credential.get().getUser()) : null;
    }

    public UserSession createInitialAdmin(String fullName, String username, char[] password, char[] confirmation)
            throws SQLException {
        validateIdentity(fullName, username);
        validatePassword(password, confirmation);
        User initialAdmin = userDao.createInitialAdmin(
                fullName.trim(),
                username.trim(),
                PasswordUtil.hash(new String(password)));
        return UserSession.authenticated(initialAdmin);
    }

    public User createUser(UserSession actor, String fullName, String username, UserRole role,
            char[] password, char[] confirmation) throws SQLException {
        requireAdmin(actor);
        validateIdentity(fullName, username);
        validatePassword(password, confirmation);
        if (role == null) {
            throw new IllegalArgumentException("Sélectionnez un rôle.");
        }
        return userDao.create(fullName.trim(), username.trim(), PasswordUtil.hash(new String(password)), role);
    }

    public User updateUser(UserSession actor, User updated, char[] newPassword, char[] confirmation)
            throws SQLException {
        requireAdmin(actor);
        validateIdentity(updated.getFullName(), updated.getUsername());
        String passwordHash = null;
        if ((newPassword != null && newPassword.length > 0)
                || (confirmation != null && confirmation.length > 0)) {
            validatePassword(newPassword, confirmation);
            passwordHash = PasswordUtil.hash(new String(newPassword));
        }
        if (updated.getId() == actor.getUserId() && !updated.isActive()) {
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        return userDao.update(updated, passwordHash);
    }

    public User deactivateUser(UserSession actor, long userId) throws SQLException {
        requireAdmin(actor);
        if (userId == actor.getUserId()) {
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        return userDao.deactivate(userId);
    }

    public java.util.List<User> listUsers(UserSession actor) throws SQLException {
        requireAdmin(actor);
        return userDao.findAll();
    }

    private static void requireAdmin(UserSession actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN) {
            throw new SecurityException("Cette opération est réservée à un administrateur.");
        }
    }

    private static void validateIdentity(String fullName, String username) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Le champ « Nom complet » est vide.");
        }
        if (fullName.trim().length() > 120) {
            throw new IllegalArgumentException("Le nom complet ne peut pas dépasser 120 caractères.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le champ « Nom d'utilisateur » est vide.");
        }
        if (!username.matches("[A-Za-z0-9._-]{3,60}")) {
            throw new IllegalArgumentException("L'identifiant doit contenir 3 à 60 caractères (lettres, chiffres, ., _ ou -).");
        }
    }

    private static void validatePassword(char[] password, char[] confirmation) {
        if (password == null || password.length == 0 || new String(password).isBlank()) {
            throw new IllegalArgumentException("Le champ « Mot de passe » est vide.");
        }
        if (password.length < 10) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 10 caractères.");
        }
        if (new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas dépasser 72 octets en UTF-8.");
        }
        if (!java.util.Arrays.equals(password, confirmation)) {
            if (confirmation == null || confirmation.length == 0 || new String(confirmation).isBlank()) {
                throw new IllegalArgumentException("Le champ « Confirmation du mot de passe » est vide.");
            }
            throw new IllegalArgumentException("La confirmation du mot de passe ne correspond pas.");
        }
    }
}
