package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.AccessControlDao;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.AccessControlOutcome;
import com.mycompany.tike.model.AccessControlResult;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AccessControlDatabaseIntegrationTest {
    private final CatalogDao catalogDao = new CatalogDao();
    private final SalesDao salesDao = new SalesDao();
    private final AccessControlService service = new AccessControlService(new AccessControlDao());

    @Test
    void checksStatusesLogsAttemptsAndPreventsConcurrentDoubleEntry() throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec SQLite.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long sellerId = 0;
        long controllerId = 0;
        Venue venue = null;
        Room room = null;
        Event event = null;
        Event secondEvent = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Long> saleIds = new ArrayList<>();
        List<String> saleNumbers = new ArrayList<>();
        try {
            sellerId = createUser(suffix, "AGENT");
            controllerId = createUser(suffix, "CONTROLEUR");
            UserSession controller = UserSession.authenticated(
                    new User(controllerId, "Contrôleur intégration", "it.ctrl." + suffix, UserRole.CONTROLEUR, true));
            venue = catalogDao.saveVenue(new Venue(0, "IT contrôle " + suffix, null, "IT", null));
            room = catalogDao.saveRoom(new Room(0, venue.id(), venue.name(), "IT contrôle " + suffix, 10));
            LocalDateTime startsAt = LocalDateTime.now().plusDays(30).withSecond(0).withNano(0);
            event = catalogDao.saveEvent(new Event(
                    0, "IT contrôle " + suffix, null, "Test", room.id(), room.name(),
                    startsAt, null, null, null, EventStatus.EN_VENTE));
            secondEvent = catalogDao.saveEvent(new Event(
                    0, "IT autre événement " + suffix, null, "Test", null, null,
                    startsAt.plusDays(2), null, null, null, EventStatus.PROGRAMME));
            TicketCategory category = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT contrôle billets " + suffix, new BigDecimal("500.00"), 5, 5, null));

            List<String> codes = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                var sale = salesDao.createSale(new SaleRequest(
                        event.id(), null, List.of(new SaleLineRequest(category.id(), 1)),
                        new BigDecimal("500.00"), PaymentMethod.ESPECES, null), sellerId);
                saleIds.add(sale.id());
                saleNumbers.add(sale.number());
                codes.add(findTicketCode(sale.id()));
            }
            List<com.mycompany.tike.model.SaleHistoryItem> eventSales =
                    service.listSales(controller, event.id());
            assertEquals(5, eventSales.size());
            assertEquals("Client non renseigné", eventSales.get(0).clientName());
            assertTrue(service.listSaleTickets(controller, event.id(), saleNumbers.get(0)).stream()
                    .anyMatch(ticket -> ticket.code().equals(codes.get(0))));

            AccessControlResult accepted = service.check(controller, event.id(), codes.get(0));
            assertEquals(AccessControlOutcome.VALIDE, accepted.outcome());
            assertEquals("IT contrôle " + suffix, accepted.ticketEventName());
            assertEquals(AccessControlOutcome.DEJA_UTILISE,
                    service.check(controller, event.id(), codes.get(0)).outcome());

            assertEquals(AccessControlOutcome.NON_TROUVE,
                    service.check(controller, event.id(), "ceci-n'est-pas-un-UUID").outcome());
            assertEquals(AccessControlOutcome.MAUVAIS_EVENEMENT,
                    service.check(controller, secondEvent.id(), codes.get(0)).outcome());

            setTicketStatus(codes.get(1), "ANNULE");
            assertEquals(AccessControlOutcome.ANNULE,
                    service.check(controller, event.id(), codes.get(1)).outcome());
            setSaleStatus(saleIds.get(2), "EN_ATTENTE");
            assertEquals(AccessControlOutcome.NON_PAYE,
                    service.check(controller, event.id(), codes.get(2)).outcome());
            setPaymentStatus(saleIds.get(4), "EN_ATTENTE");
            assertEquals(AccessControlOutcome.NON_PAYE,
                    service.check(controller, event.id(), codes.get(4)).outcome());

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            long eventId = event.id();
            String concurrentCode = codes.get(3);
            Future<AccessControlOutcome> first = executor.submit(
                    () -> attemptCheck(controller, eventId, concurrentCode, ready, start));
            Future<AccessControlOutcome> second = executor.submit(
                    () -> attemptCheck(controller, eventId, concurrentCode, ready, start));
            ready.await();
            start.countDown();
            List<AccessControlOutcome> concurrentResults = List.of(first.get(), second.get());
            assertEquals(1, concurrentResults.stream().filter(r -> r == AccessControlOutcome.VALIDE).count());
            assertEquals(1, concurrentResults.stream()
                    .filter(r -> r == AccessControlOutcome.DEJA_UTILISE).count());

            assertEquals(9, count("SELECT COUNT(*) FROM controles_acces WHERE evenement_id IN (?, ?)",
                    event.id(), secondEvent.id()));
            assertEquals(2, count("SELECT COUNT(*) FROM controles_acces WHERE evenement_id = ? AND resultat = 'VALIDE'",
                    event.id()));
        } finally {
            executor.shutdownNow();
            cleanup(controllerId, sellerId, event, secondEvent, room, venue);
        }
    }

    private AccessControlOutcome attemptCheck(
            UserSession controller, long eventId, String code, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return service.check(controller, eventId, code).outcome();
    }

    private static long createUser(String suffix, String role) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
                     VALUES (?, ?, 'test-only-not-a-password', ?, TRUE)
                     RETURNING id
                     """)) {
            statement.setString(1, "IT " + role + " " + suffix);
            statement.setString(2, "it." + role.toLowerCase(java.util.Locale.ROOT) + "." + suffix);
            statement.setString(3, role);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String findTicketCode(long saleId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT code_unique FROM billets WHERE vente_id = ?")) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Le billet de test n'a pas été créé.");
                }
                return result.getString(1);
            }
        }
    }

    private static void setTicketStatus(String code, String status) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE billets SET statut = ? WHERE code_unique = ?")) {
            statement.setString(1, status);
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }

    private static void setSaleStatus(long saleId, String status) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE ventes SET statut = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setLong(2, saleId);
            statement.executeUpdate();
        }
    }

    private static void setPaymentStatus(long saleId, String status) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE paiements SET statut = ? WHERE vente_id = ?")) {
            statement.setString(1, status);
            statement.setLong(2, saleId);
            statement.executeUpdate();
        }
    }

    private static int count(String sql, long firstId, long secondId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, firstId);
            statement.setLong(2, secondId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int count(String sql, long eventId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void cleanup(
            long controllerId, long sellerId, Event event, Event secondEvent,
            Room room, Venue venue) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (event != null || secondEvent != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM controles_acces WHERE evenement_id IN (?, ?)")) {
                    statement.setObject(1, event == null ? null : event.id());
                    statement.setObject(2, secondEvent == null ? null : secondEvent.id());
                    statement.executeUpdate();
                }
            }
            if (event != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM billets WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM paiements
                        WHERE vente_id IN (SELECT id FROM ventes WHERE evenement_id = ?)
                        """)) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM vente_lignes WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM ventes WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM categories_billets WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM evenements WHERE id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
            }
            if (secondEvent != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM evenements WHERE id = ?")) {
                    statement.setLong(1, secondEvent.id());
                    statement.executeUpdate();
                }
            }
            if (room != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM salles WHERE id = ?")) {
                    statement.setLong(1, room.id());
                    statement.executeUpdate();
                }
            }
            if (venue != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM lieux WHERE id = ?")) {
                    statement.setLong(1, venue.id());
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM journal_activites WHERE utilisateur_id IN (?, ?)")) {
                statement.setLong(1, controllerId);
                statement.setLong(2, sellerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM utilisateurs WHERE id IN (?, ?)")) {
                statement.setLong(1, controllerId);
                statement.setLong(2, sellerId);
                statement.executeUpdate();
            }
        }
    }
}
