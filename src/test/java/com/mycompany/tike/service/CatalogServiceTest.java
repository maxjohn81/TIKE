package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {
    private final CatalogService service = new CatalogService(new CatalogDao());
    private final UserSession admin = UserSession.authenticated(
            new User(1, "Administrateur TIKE", "admin.test", UserRole.ADMIN, true));
    private final UserSession agent = UserSession.authenticated(
            new User(2, "Agent TIKE", "agent.test", UserRole.AGENT, true));

    @Test
    void onlyAdministratorsCanManageVenues() {
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> service.saveVenue(agent, new Venue(0, "Salle", null, null, null)));

        assertEquals("Cette opération n'est pas autorisée pour votre rôle.", error.getMessage());
    }

    @Test
    void venueNameIsRequiredBeforeAccessingTheDatabase() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveVenue(admin, new Venue(0, "  ", null, null, null)));

        assertEquals("Le nom du lieu est obligatoire.", error.getMessage());
    }

    @Test
    void roomRequiresPositiveCapacity() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRoom(admin, new Room(0, 1, "Lieu", "Salle", 0)));

        assertEquals("La capacité doit être supérieure à zéro.", error.getMessage());
    }

    @Test
    void eventEndMustBeAfterItsStart() {
        LocalDateTime startsAt = LocalDateTime.of(2027, 1, 10, 18, 0);
        Event event = new Event(0, "Concert", null, "Musique", null, null,
                startsAt, startsAt, null, null, EventStatus.BROUILLON);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.saveEvent(admin, event));

        assertEquals("La date de fin doit être postérieure à la date de début.", error.getMessage());
    }

    @Test
    void salesMustCloseNoLaterThanEventStart() {
        LocalDateTime startsAt = LocalDateTime.of(2027, 1, 10, 18, 0);
        Event event = new Event(0, "Concert", null, "Musique", null, null,
                startsAt, null, startsAt.minusDays(1), startsAt.plusMinutes(1), EventStatus.EN_VENTE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.saveEvent(admin, event));

        assertEquals("Les ventes doivent fermer au plus tard au début de l'événement.", error.getMessage());
    }

    @Test
    void categoryRequiresNonnegativePriceAndQuantity() {
        TicketCategory negativePrice = new TicketCategory(
                0, 1, "Standard", new BigDecimal("-1.00"), 10, 10, null);
        assertEquals("Le prix doit être supérieur ou égal à zéro.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.saveCategory(admin, negativePrice)).getMessage());

        TicketCategory negativeQuantity = new TicketCategory(
                0, 1, "Standard", new BigDecimal("10.00"), -1, -1, null);
        assertEquals("La quantité totale ne peut pas être négative.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.saveCategory(admin, negativeQuantity)).getMessage());
    }
}
