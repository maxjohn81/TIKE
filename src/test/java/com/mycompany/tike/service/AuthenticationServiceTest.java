package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.UserDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    private final AuthenticationService authenticationService = new AuthenticationService(new UserDao());

    @Test
    void nonAdministratorCannotCreateAccounts() {
        UserSession agent = UserSession.authenticated(
                new User(1, "Agent TIKE", "agent.test", UserRole.AGENT, true));

        assertThrows(SecurityException.class, () -> authenticationService.createUser(
                agent, "Test", "test.user", UserRole.AGENT, "Password123".toCharArray(),
                "Password123".toCharArray()));
    }

    @Test
    void nonAdministratorCannotListAccounts() {
        UserSession controller = UserSession.authenticated(
                new User(2, "Controleur TIKE", "control.test", UserRole.CONTROLEUR, true));

        assertThrows(SecurityException.class, () -> authenticationService.listUsers(controller));
    }

    @Test
    void administratorCannotDeactivateTheirOwnAccount() {
        User admin = new User(3, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true);
        UserSession session = UserSession.authenticated(admin);
        User deactivatedAdmin = new User(3, "Administrateur TIKE", "admin.test", UserRole.ADMIN, false);

        assertThrows(IllegalArgumentException.class, () -> authenticationService.updateUser(
                session, deactivatedAdmin, null, null));
        assertThrows(IllegalArgumentException.class, () -> authenticationService.deactivateUser(session, 3));
    }

    @Test
    void initialAdminPasswordRequiresConfirmationAndMinimumLength() {
        assertEquals("Le champ « Nom complet » est vide.", assertThrows(
                IllegalArgumentException.class,
                () -> authenticationService.createInitialAdmin(
                        " ", "admin.test", "Password123".toCharArray(), "Password123".toCharArray())).getMessage());
        assertEquals("Le champ « Nom d'utilisateur » est vide.", assertThrows(
                IllegalArgumentException.class,
                () -> authenticationService.createInitialAdmin(
                        "Administrateur", " ", "Password123".toCharArray(), "Password123".toCharArray())).getMessage());
        assertEquals("Le champ « Mot de passe » est vide.", assertThrows(
                IllegalArgumentException.class,
                () -> authenticationService.createInitialAdmin(
                        "Administrateur", "admin.test", new char[0], new char[0])).getMessage());
        assertEquals("Le champ « Confirmation du mot de passe » est vide.", assertThrows(
                IllegalArgumentException.class,
                () -> authenticationService.createInitialAdmin(
                        "Administrateur", "admin.test", "Password123".toCharArray(), new char[0])).getMessage());
        assertThrows(IllegalArgumentException.class, () -> authenticationService.createInitialAdmin(
                "Administrateur", "admin.test", "short".toCharArray(), "short".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> authenticationService.createInitialAdmin(
                "Administrateur", "admin.test", "Password123".toCharArray(), "Different123".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> authenticationService.createInitialAdmin(
                "Administrateur", "admin.test", "é".repeat(37).toCharArray(), "é".repeat(37).toCharArray()));
    }
}
