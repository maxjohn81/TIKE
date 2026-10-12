package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.Ticket;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SalesDatabaseIntegrationTest {
    private final CatalogDao catalogDao = new CatalogDao();
    private final SalesDao salesDao = new SalesDao();

    @Test
    void salePaymentTicketsRollbackAndConcurrentStockAreAtomic() throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec PostgreSQL.");

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
                    new User(sellerId, "Agent intégration", "it.sales." + suffix, UserRole.AGENT, true));
            venue = catalogDao.saveVenue(new Venue(0, "IT ventes " + suffix, null, "IT", null));
            room = catalogDao.saveRoom(new Room(0, venue.id(), venue.name(), "IT ventes " + suffix, 3));
            LocalDateTime startsAt = LocalDateTime.now().plusDays(30).withSecond(0).withNano(0);
            event = catalogDao.saveEvent(new Event(
                    0, "IT vente " + suffix, null, "Test", room.id(), room.name(),
                    startsAt, null, null, null, EventStatus.EN_VENTE));
            TicketCategory category = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT billets " + suffix, new BigDecimal("1250.00"), 3, 3, null));
            String phone = "+261 (33) 000-00"
                    + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);
            client = new SalesService(salesDao).saveClient(
                    seller, new Client(0, "IT client " + suffix, phone, null));
            assertEquals(phone.replaceAll("[\\s().-]", ""), client.phone());
            long savedEventId = event.id();
            long savedCategoryId = category.id();
            long savedClientId = client.id();
            long savedSellerId = sellerId;

            var initial = salesDao.createSale(new SaleRequest(
                    savedEventId, savedClientId, List.of(new SaleLineRequest(savedCategoryId, 2)),
                    new BigDecimal("2500.00"), PaymentMethod.ESPECES, null), savedSellerId);
            assertEquals(new BigDecimal("2500.00"), initial.total());
            assertEquals(2, initial.ticketCount());
            assertEquals(2, count("SELECT COUNT(*) FROM billets WHERE vente_id = ?", initial.id()));
            assertEquals(1, count("SELECT COUNT(*) FROM paiements WHERE vente_id = ? AND statut = 'PAYE'", initial.id()));
            List<Ticket> issuedTickets = new SalesService(salesDao).findPaidSaleTickets(seller, initial.number());
            assertEquals(2, issuedTickets.size());
            assertEquals(2, issuedTickets.stream().map(Ticket::uniqueCode).distinct().count());
            assertEquals("IT vente " + suffix, issuedTickets.get(0).eventName());
            assertEquals("IT client " + suffix, issuedTickets.get(0).customerName());
            assertEquals(issuedTickets, new SalesService(salesDao)
                    .findPaidSaleTickets(seller, initial.number().toLowerCase(java.util.Locale.ROOT)));
            assertEquals(2, count("SELECT COUNT(*) FROM billets WHERE vente_id = ?", initial.id()));
            assertEquals(1, catalogDao.findCategories(savedEventId).get(0).availableQuantity());
            ReportingDao reportingDao = new ReportingDao();
            var periodSales = reportingDao.findSales(
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), savedEventId);
            assertEquals(1, periodSales.size());
            assertEquals(initial.number(), periodSales.get(0).saleNumber());
            assertEquals(2, periodSales.get(0).ticketCount());
            assertEquals(1, reportingDao.findSales(null, null, savedEventId).size());
            var fill = reportingDao.findEventFill(null, null, savedEventId).get(0);
            assertEquals(3, fill.capacity());
            assertEquals(2, fill.sold());
            assertEquals(1, count(
                    "SELECT COUNT(*) FROM journal_activites WHERE action = 'VENTE_CREEE' AND objet_id = ?",
                    initial.id()));
            var activity = reportingDao.findActivity(
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), initial.number());
            assertEquals(1, activity.size());
            assertEquals("VENTE_CREEE", activity.get(0).action());
            assertFalse(reportingDao.findActivity(null, null, "").isEmpty());
            var metrics = reportingDao.dashboardMetrics();
            assertTrue(metrics.activeEvents() >= 1);
            assertTrue(metrics.soldTickets() >= 2);
            assertTrue(metrics.revenue().compareTo(new BigDecimal("2500.00")) >= 0);
            client = new SalesService(salesDao).saveClient(
                    seller, new Client(savedClientId, "IT client modifié " + suffix, client.phone(), null));

            assertThrows(IllegalArgumentException.class, () -> salesDao.createSale(new SaleRequest(
                    savedEventId, savedClientId, List.of(new SaleLineRequest(savedCategoryId, 2)),
                    new BigDecimal("2500.00"), PaymentMethod.ESPECES, null), savedSellerId));
            assertEquals(1, catalogDao.findCategories(savedEventId).get(0).availableQuantity());

            assertThrows(IllegalArgumentException.class, () -> salesDao.createSale(new SaleRequest(
                    savedEventId, savedClientId, List.of(new SaleLineRequest(savedCategoryId, 1)),
                    new BigDecimal("1.00"), PaymentMethod.ESPECES, null), savedSellerId));
            assertEquals(1, catalogDao.findCategories(savedEventId).get(0).availableQuantity());

            int salesBeforeFailure = count("SELECT COUNT(*) FROM ventes WHERE evenement_id = ?", savedEventId);
            SaleRequest invalidLateInsert = new SaleRequest(
                    savedEventId, savedClientId, List.of(new SaleLineRequest(savedCategoryId, 1)),
                    new BigDecimal("1250.00"), PaymentMethod.MOBILE_MONEY, "x".repeat(121));
            assertThrows(SQLException.class, () -> salesDao.createSale(invalidLateInsert, savedSellerId));
            assertEquals(salesBeforeFailure,
                    count("SELECT COUNT(*) FROM ventes WHERE evenement_id = ?", savedEventId));
            assertEquals(1, catalogDao.findCategories(savedEventId).get(0).availableQuantity());

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            long transactionSellerId = savedSellerId;
            SaleRequest lastTicket = new SaleRequest(
                    savedEventId, savedClientId, List.of(new SaleLineRequest(savedCategoryId, 1)),
                    new BigDecimal("1250.00"), PaymentMethod.CARTE, null);
            Future<Boolean> first = executor.submit(
                    () -> attemptConcurrentSale(lastTicket, transactionSellerId, ready, start));
            Future<Boolean> second = executor.submit(
                    () -> attemptConcurrentSale(lastTicket, transactionSellerId, ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertEquals(0, catalogDao.findCategories(savedEventId).get(0).availableQuantity());
            assertEquals(2, salesDao.findClientHistory(savedClientId).size());
            assertEquals(3, count("""
                    SELECT COUNT(*) FROM billets b JOIN ventes v ON v.id = b.vente_id
                    WHERE v.evenement_id = ? AND v.statut = 'PAYEE' AND b.statut = 'VALIDE'
                    """, savedEventId));

            Event cancelledEvent = catalogDao.saveEvent(new Event(
                    0, "IT annulé " + suffix, null, "Test", room.id(), room.name(),
                    startsAt.plusDays(2), null, null, null, EventStatus.ANNULE));
            assertThrows(IllegalArgumentException.class, () -> salesDao.createSale(
                    new SaleRequest(cancelledEvent.id(), savedClientId,
                            List.of(new SaleLineRequest(savedCategoryId, 1)), new BigDecimal("1250.00"),
                            PaymentMethod.ESPECES, null),
                    savedSellerId));
            deleteEvent(cancelledEvent.id());
        } finally {
            executor.shutdownNow();
            cleanup(sellerId, client, event, room, venue);
        }
    }

    private boolean attemptConcurrentSale(
            SaleRequest request, long sellerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            salesDao.createSale(request, sellerId);
            return true;
        } catch (IllegalArgumentException noStock) {
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
            statement.setString(1, "Agent intégration " + suffix);
            statement.setString(2, "it.sales." + suffix);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
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

    private static void deleteEvent(long eventId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM evenements WHERE id = ?")) {
            statement.setLong(1, eventId);
            statement.executeUpdate();
        }
    }

    private static void cleanup(long sellerId, Client client, Event event, Room room, Venue venue)
            throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (event != null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM billets WHERE evenement_id = ?
                        """)) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM paiements WHERE vente_id IN (SELECT id FROM ventes WHERE evenement_id = ?)
                        """)) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM vente_lignes WHERE evenement_id = ?
                        """)) {
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
