package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.ReservationDao;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.ReservationLineRequest;
import com.mycompany.tike.model.ReservationRequest;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
    private final ReservationService service = new ReservationService(new ReservationDao());
    private final UserSession admin = UserSession.authenticated(
            new User(1, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true));
    private final UserSession agent = UserSession.authenticated(
            new User(2, "Agent TIKE", "agent.test", UserRole.AGENT, true));
    private final UserSession controller = UserSession.authenticated(
            new User(3, "Contrôleur TIKE", "controller.test", UserRole.CONTROLEUR, true));

    @Test
    void onlySalesRolesCanManageReservations() {
        assertEquals("Cette opération est réservée aux administrateurs et aux agents de billetterie.",
                assertThrows(SecurityException.class, () -> service.list(controller, "", null)).getMessage());
        assertThrows(SecurityException.class,
                () -> service.expirePending(controller));
    }

    @Test
    void createRequiresClientAndAtLeastOneUniquePositiveLine() {
        assertEquals("Sélectionnez un client pour cette réservation.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create(agent, new ReservationRequest(
                                1, null, List.of(new ReservationLineRequest(1, 1)))))
                        .getMessage());
        assertEquals("Ajoutez au moins une catégorie à la réservation.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create(agent, new ReservationRequest(1, 2L, List.of())))
                        .getMessage());
        assertEquals("La quantité doit être supérieure à zéro.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create(admin, new ReservationRequest(
                                1, 2L, List.of(new ReservationLineRequest(3, 0)))))
                        .getMessage());
        assertEquals("Une catégorie ne peut apparaître qu'une fois dans la réservation.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create(admin, new ReservationRequest(1, 2L, List.of(
                                new ReservationLineRequest(3, 1), new ReservationLineRequest(3, 2)))))
                        .getMessage());
    }

    @Test
    void conversionRequiresPaymentMethodAndValidId() {
        assertEquals("Sélectionnez une réservation.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.convert(agent, 0, PaymentMethod.ESPECES, null)).getMessage());
        assertEquals("Sélectionnez un mode de paiement.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.convert(agent, 1, null, null)).getMessage());
    }
}
