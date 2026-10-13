package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.dao.ReservationDao;
import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.Reservation;
import com.mycompany.tike.model.ReservationLineRequest;
import com.mycompany.tike.model.ReservationRequest;
import com.mycompany.tike.model.ReservationStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ReservationDatabaseIntegrationTest {
    private final CatalogDao catalogDao = new CatalogDao();
    private final ReservationDao reservationDao = new ReservationDao();
    private final SalesDao salesDao = new SalesDao();
    private final ReservationService reservationService = new ReservationService(reservationDao);

    @Test
    void reservationsHoldReleaseExpireAndConvertOnlyOnce() throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec SQLite.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long sellerId = 0;
        Venue venue = null;
        Room room = null;
        Event event = null;
        Client client = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            sellerId = createSeller(suffix);
            UserSession seller = UserSession.authenticated(
                    new User(sellerId, "Agent réservations " + suffix, "it.res." + suffix, UserRole.AGENT, true));
            venue = catalogDao.saveVenue(new Venue(0, "IT réservation " + suffix, null, "IT", null));
            room = catalogDao.saveRoom(new Room(0, venue.id(), venue.name(), "IT réservation " + suffix, 20));
            LocalDateTime startsAt = LocalDateTime.now().plusDays(40).withSecond(0).withNano(0);
            event = catalogDao.saveEvent(new Event(
                    0, "IT réservation " + suffix, null, "Test", room.id(), room.name(),
                    startsAt, null, null, null, EventStatus.EN_VENTE));
            TicketCategory category = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT catégorie " + suffix, new BigDecimal("1000.00"), 5, 5, null));
            client = salesDao.saveClient(new Client(0, "IT client " + suffix, "+261" + suffix, null));
            long eventId = event.id();
            long categoryId = category.id();
            long clientId = client.id();
            long actorId = sellerId;

            Reservation held = reservationService.create(seller,
                    new ReservationRequest(eventId, clientId, List.of(new ReservationLineRequest(categoryId, 4))));
            assertEquals(4, held.lines().get(0).quantity());
            assertEquals(1, catalogDao.findCategories(eventId).get(0).availableQuantity());
            assertThrows(IllegalArgumentException.class, () -> reservationService.create(seller,
                    new ReservationRequest(eventId, clientId, List.of(new ReservationLineRequest(categoryId, 2)))));
            assertThrows(IllegalArgumentException.class, () -> salesDao.createSale(new SaleRequest(
                    eventId, clientId, List.of(new SaleLineRequest(categoryId, 2)),
                    new BigDecimal("2000.00"), PaymentMethod.ESPECES, null), actorId));

            reservationService.cancel(seller, held.id());
            assertEquals(5, catalogDao.findCategories(eventId).get(0).availableQuantity());
            assertThrows(IllegalArgumentException.class, () -> reservationService.cancel(seller, held.id()));

            Reservation expired = reservationService.create(seller,
                    new ReservationRequest(eventId, clientId, List.of(new ReservationLineRequest(categoryId, 4))));
            setExpired(expired.id());
            List<Reservation> afterExpiry = reservationService.list(seller, suffix, ReservationStatus.EXPIREE);
            assertEquals(1, afterExpiry.size());
            assertEquals(expired.id(), afterExpiry.get(0).id());
            assertEquals(5, catalogDao.findCategories(eventId).get(0).availableQuantity());

            Reservation pending = reservationService.create(seller,
                    new ReservationRequest(eventId, clientId, List.of(new ReservationLineRequest(categoryId, 2))));
            assertThrows(SQLException.class, () -> reservationDao.convert(
                    pending.id(), PaymentMethod.ESPECES, "x".repeat(121), actorId));
            List<Reservation> pendingAfterRollback = reservationService.list(
                    seller, suffix, ReservationStatus.EN_ATTENTE);
            assertEquals(1, pendingAfterRollback.size());
            assertEquals(pending.id(), pendingAfterRollback.get(0).id());
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            long reservationId = pending.id();
            Future<Boolean> first = executor.submit(
                    () -> attemptConversion(reservationId, seller, ready, start));
            Future<Boolean> second = executor.submit(
                    () -> attemptConversion(reservationId, seller, ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            List<Reservation> converted = reservationService.list(seller, suffix, ReservationStatus.CONFIRMEE);
            assertEquals(1, converted.size());
            assertEquals(2, count("""
                    SELECT COUNT(*) FROM billets b JOIN ventes v ON v.id = b.vente_id
                    WHERE v.evenement_id = ? AND v.statut = 'PAYEE'
                    """, eventId));
            assertEquals(3, catalogDao.findCategories(eventId).get(0).availableQuantity());
            assertThrows(IllegalArgumentException.class,
                    () -> reservationService.convert(seller, reservationId, PaymentMethod.ESPECES, null));
        } finally {
            executor.shutdownNow();
            cleanup(sellerId, client, event, room, venue);
        }
    }

    private boolean attemptConversion(
            long reservationId, UserSession actor, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            reservationService.convert(actor, reservationId, PaymentMethod.ESPECES, null);
            return true;
        } catch (IllegalArgumentException alreadyConverted) {
            return false;
        }
    }

    private static long createSeller(String suffix) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
                     VALUES (?, ?, 'test-only-not-a-password', 'AGENT', TRUE)
                     RETURNING id
                     """)) {
            statement.setString(1, "Agent réservation " + suffix);
            statement.setString(2, "it.res." + suffix);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void setExpired(long reservationId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE reservations
                     SET cree_le = datetime('now', '-25 hours'),
                         expire_le = datetime('now', '-1 minute')
                     WHERE id = ?
                     """)) {
            statement.setLong(1, reservationId);
            statement.executeUpdate();
        }
    }

    private static int count(String sql, long id) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void cleanup(long sellerId, Client client, Event event, Room room, Venue venue)
            throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (event != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM reservation_lignes WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM reservations WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM billets WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM paiements WHERE vente_id IN (SELECT id FROM ventes WHERE evenement_id = ?)
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
            if (client != null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clients WHERE id = ?")) {
                    statement.setLong(1, client.id());
                    statement.executeUpdate();
                }
            }
            if (room != null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM salles WHERE id = ?")) {
                    statement.setLong(1, room.id());
                    statement.executeUpdate();
                }
            }
            if (venue != null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM lieux WHERE id = ?")) {
                    statement.setLong(1, venue.id());
                    statement.executeUpdate();
                }
            }
            if (sellerId != 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM journal_activites WHERE utilisateur_id = ?")) {
                    statement.setLong(1, sellerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM utilisateurs WHERE id = ?")) {
                    statement.setLong(1, sellerId);
                    statement.executeUpdate();
                }
            }
        }
    }
}
