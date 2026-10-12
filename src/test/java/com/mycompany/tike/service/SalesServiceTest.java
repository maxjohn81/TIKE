package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesServiceTest {
    private final SalesService service = new SalesService(new SalesDao());
    private final UserSession admin = UserSession.authenticated(
            new User(1, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true));
    private final UserSession agent = UserSession.authenticated(
            new User(2, "Agent TIKE", "agent.test", UserRole.AGENT, true));
    private final UserSession controller = UserSession.authenticated(
            new User(3, "Contrôleur TIKE", "controller.test", UserRole.CONTROLEUR, true));

    @Test
    void controllerCannotManageClientsOrSales() {
        assertEquals("Cette opération est réservée aux administrateurs et aux agents de billetterie.",
                assertThrows(SecurityException.class, () -> service.listClients(controller, ""))
                        .getMessage());
        assertThrows(SecurityException.class, () -> service.createSale(controller,
                new SaleRequest(1, null, List.of(new SaleLineRequest(1, 1)),
                        BigDecimal.ZERO, PaymentMethod.ESPECES, null)));
        assertThrows(SecurityException.class, () -> service.findPaidSaleTickets(controller, "VT-TEST"));
    }

    @Test
    void ticketLookupRequiresASaleNumber() {
        assertEquals("Saisissez un numéro de vente valide.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.findPaidSaleTickets(agent, "  ")).getMessage());
    }

    @Test
    void clientNameIsRequiredAndEmailMustBeValid() {
        assertEquals("Le nom complet du client est obligatoire.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.saveClient(agent, new Client(0, "  ", null, null))).getMessage());
        assertEquals("Saisissez une adresse e-mail valide (facultative).",
                assertThrows(IllegalArgumentException.class,
                        () -> service.saveClient(agent, new Client(0, "Client", null, "invalid")))
                        .getMessage());
    }

    @Test
    void saleMustContainAValidEventPaymentAndUniquePositiveLines() {
        assertEquals("Sélectionnez un événement.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createSale(agent,
                                new SaleRequest(0, null, List.of(), BigDecimal.ZERO, PaymentMethod.ESPECES, null)))
                        .getMessage());
        assertEquals("Sélectionnez un mode de paiement.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createSale(agent,
                                new SaleRequest(1, null, List.of(new SaleLineRequest(1, 1)),
                                        BigDecimal.ZERO, null, null)))
                        .getMessage());
        assertEquals("Ajoutez au moins une catégorie au panier.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createSale(agent,
                                new SaleRequest(1, null, List.of(), BigDecimal.ZERO, PaymentMethod.ESPECES, null)))
                        .getMessage());
        assertEquals("La quantité doit être supérieure à zéro.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createSale(agent,
                                new SaleRequest(1, null, List.of(new SaleLineRequest(1, 0)),
                                        BigDecimal.ZERO, PaymentMethod.ESPECES, null)))
                        .getMessage());
        assertEquals("Une catégorie ne peut apparaître qu'une fois dans le panier.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createSale(agent,
                                new SaleRequest(1, null,
                                        List.of(new SaleLineRequest(1, 1), new SaleLineRequest(1, 2)),
                                        BigDecimal.ZERO, PaymentMethod.ESPECES, null)))
                        .getMessage());
    }
}
