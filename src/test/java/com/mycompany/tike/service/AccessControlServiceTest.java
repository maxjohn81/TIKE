package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.AccessControlDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import org.junit.jupiter.api.Test;

class AccessControlServiceTest {
    private final AccessControlService service = new AccessControlService(new AccessControlDao());
    private final UserSession agent = UserSession.authenticated(
            new User(2, "Agent TIKE", "agent.test", UserRole.AGENT, true));
    private final UserSession controller = UserSession.authenticated(
            new User(3, "Contrôleur TIKE", "controller.test", UserRole.CONTROLEUR, true));
    private final UserSession admin = UserSession.authenticated(
            new User(1, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true));

    @Test
    void onlyAdministratorsAndControllersMayCheckTickets() {
        assertThrows(SecurityException.class, () -> service.listEvents(agent));
        assertThrows(SecurityException.class, () -> service.check(agent, 1, "ticket-code"));
    }

    @Test
    void checkRequiresAnEventAndNonEmptyCode() {
        assertEquals("Sélectionnez l'événement à contrôler.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.check(controller, 0, "00000000-0000-0000-0000-000000000000"))
                        .getMessage());
        assertEquals("Saisissez le code du billet.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.check(admin, 1, "  ")).getMessage());
    }
}
