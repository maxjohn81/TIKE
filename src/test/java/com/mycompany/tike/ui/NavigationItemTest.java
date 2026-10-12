package com.mycompany.tike.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.tike.model.UserRole;
import org.junit.jupiter.api.Test;

class NavigationItemTest {

    @Test
    void administratorCanSeeAdministrativeAndBusinessSections() {
        assertTrue(NavigationItem.USERS.isAvailableTo(UserRole.ADMIN));
        assertTrue(NavigationItem.SALES.isAvailableTo(UserRole.ADMIN));
        assertTrue(NavigationItem.ACCESS_CONTROL.isAvailableTo(UserRole.ADMIN));
    }

    @Test
    void ticketAgentCannotSeeAdministrativeSections() {
        assertTrue(NavigationItem.SALES.isAvailableTo(UserRole.AGENT));
        assertTrue(NavigationItem.CLIENTS.isAvailableTo(UserRole.AGENT));
        assertFalse(NavigationItem.ACCESS_CONTROL.isAvailableTo(UserRole.AGENT));
        assertFalse(NavigationItem.USERS.isAvailableTo(UserRole.AGENT));
        assertFalse(NavigationItem.REPORTS.isAvailableTo(UserRole.AGENT));
    }

    @Test
    void accessControllerOnlySeesHomeAndAccessControl() {
        assertTrue(NavigationItem.HOME.isAvailableTo(UserRole.CONTROLEUR));
        assertTrue(NavigationItem.ACCESS_CONTROL.isAvailableTo(UserRole.CONTROLEUR));
        assertFalse(NavigationItem.SALES.isAvailableTo(UserRole.CONTROLEUR));
        assertFalse(NavigationItem.REPORTS.isAvailableTo(UserRole.CONTROLEUR));
    }
}
