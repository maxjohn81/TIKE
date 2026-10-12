package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.RefundDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import org.junit.jupiter.api.Test;

class RefundServiceTest {
    private final RefundService service = new RefundService(new RefundDao());
    private final UserSession admin = UserSession.authenticated(
            new User(1, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true));
    private final UserSession agent = UserSession.authenticated(
            new User(2, "Agent TIKE", "agent.test", UserRole.AGENT, true));

    @Test
    void onlyAdministratorsMayRecordRefunds() {
        assertEquals("Seuls les administrateurs peuvent enregistrer un remboursement.",
                assertThrows(SecurityException.class,
                        () -> service.refundFullSale(agent, "VT-TEST", "Motif"))
                        .getMessage());
    }

    @Test
    void refundRequiresASaleNumberAndReason() {
        assertEquals("Saisissez un numéro de vente valide.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.refundFullSale(admin, "  ", "Motif"))
                        .getMessage());
        assertEquals("Le motif du remboursement est obligatoire.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.refundFullSale(admin, "VT-TEST", "  "))
                        .getMessage());
    }

    @Test
    void refundReasonIsLimitedToFiveHundredCharacters() {
        assertEquals("Le motif ne peut pas dépasser 500 caractères.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.refundFullSale(admin, "VT-TEST", "x".repeat(501)))
                        .getMessage());
    }
}
