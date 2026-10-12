package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportingServiceTest {
    private final ReportingService service = new ReportingService(new ReportingDao());

    @Test
    void reportingRequiresAdministratorRole() {
        UserSession agent = session(UserRole.AGENT);
        assertThrows(SecurityException.class, () -> service.events(agent));
        assertThrows(SecurityException.class, () -> service.sales(agent, null, null, null));
        assertThrows(SecurityException.class, () -> service.activity(agent, null, null, ""));
    }

    @Test
    void reportingRejectsInvertedDateRangeBeforeDatabaseAccess() {
        UserSession administrator = session(UserRole.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.sales(
                administrator, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), null));
        assertThrows(IllegalArgumentException.class, () -> service.eventFill(
                administrator, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), null));
    }

    private static UserSession session(UserRole role) {
        return UserSession.authenticated(new User(1, "Test", "test", role, true));
    }
}
