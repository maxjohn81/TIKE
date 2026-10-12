package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.AccessControlDao;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.dao.RefundDao;
import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.AccessControlOutcome;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.RefundResult;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RefundDatabaseIntegrationTest {
    private final CatalogDao catalogDao = new CatalogDao();
    private final SalesDao salesDao = new SalesDao();
    private final RefundService refundService = new RefundService(new RefundDao());

    @Test
    void fullRefundInvalidatesTicketsRecordsHistoryAndRestoresStock() throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec PostgreSQL.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long administratorId = 0;
        Venue venue = null;
        Room room = null;
        Event event = null;
        try {
            administratorId = createAdministrator(suffix);
            UserSession administrator = UserSession.authenticated(
                    new User(administratorId, "Administrateur intégration", "it.refund." + suffix,
                            UserRole.ADMIN, true));
            venue = catalogDao.saveVenue(new Venue(0, "IT remboursements " + suffix, null, "IT", null));
            room = catalogDao.saveRoom(new Room(0, venue.id(), venue.name(), "IT remboursements " + suffix, 10));
            LocalDateTime startsAt = LocalDateTime.now().plusDays(20).withSecond(0).withNano(0);
            event = catalogDao.saveEvent(new Event(
                    0, "IT remboursement " + suffix, null, "Test", room.id(), room.name(),
                    startsAt, null, null, null, EventStatus.EN_VENTE));
            TicketCategory category = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT billets remboursables " + suffix,
                    new BigDecimal("1750.00"), 8, 8, null));
            TicketCategory freeCategory = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT billets gratuits " + suffix,
                    BigDecimal.ZERO, 1, 1, null));

            var refundableSale = createSale(administratorId, event.id(), category.id(), 2,
                    new BigDecimal("3500.00"));
            assertEquals(6, catalogDao.findCategories(event.id()).stream()
                    .filter(item -> item.id() == category.id()).findFirst().orElseThrow().availableQuantity());

            RefundResult refund = refundService.refundFullSale(
                    administrator, refundableSale.number().toLowerCase(java.util.Locale.ROOT),
                    "Événement annulé par l’organisateur");
            assertEquals(refundableSale.number(), refund.saleNumber());
            assertEquals(new BigDecimal("3500.00"), refund.amount());
            assertEquals(2, refund.ticketCount());
            assertNotEquals("", refund.refundNumber());
            assertEquals(8, catalogDao.findCategories(event.id()).stream()
                    .filter(item -> item.id() == category.id()).findFirst().orElseThrow().availableQuantity());
            assertEquals(2, count(
                    "SELECT COUNT(*) FROM billets WHERE vente_id = ? AND statut = 'REMBOURSE'",
                    refundableSale.id()));
            assertEquals(1, count(
                    "SELECT COUNT(*) FROM paiements WHERE vente_id = ? AND statut = 'REMBOURSE'",
                    refundableSale.id()));
            assertEquals(1, count("SELECT COUNT(*) FROM remboursements WHERE vente_id = ?", refundableSale.id()));
            assertEquals("REMBOURSEE", saleStatus(refundableSale.id()));
            assertEquals(1, count(
                    "SELECT COUNT(*) FROM journal_activites WHERE action = 'REMBOURSEMENT_ENREGISTRE' "
                            + "AND utilisateur_id = ?",
                    administratorId));
            assertThrows(IllegalArgumentException.class, () -> refundService.refundFullSale(
                    administrator, refundableSale.number(), "Deuxième tentative"));
            assertEquals(1, count("SELECT COUNT(*) FROM remboursements WHERE vente_id = ?", refundableSale.id()));

            var usedSale = createSale(administratorId, event.id(), category.id(), 1, new BigDecimal("1750.00"));
            String usedCode = ticketCode(usedSale.id());
            assertEquals(AccessControlOutcome.VALIDE,
                    new AccessControlService(new AccessControlDao()).check(
                            administrator, event.id(), usedCode).outcome());
            assertThrows(IllegalArgumentException.class, () -> refundService.refundFullSale(
                    administrator, usedSale.number(), "Billet déjà utilisé"));
            assertEquals("PAYEE", saleStatus(usedSale.id()));
            assertEquals(0, count("SELECT COUNT(*) FROM remboursements WHERE vente_id = ?", usedSale.id()));

            var afterEventSale = createSale(
                    administratorId, event.id(), category.id(), 1, new BigDecimal("1750.00"));
            var freeSale = createSale(administratorId, event.id(), freeCategory.id(), 1, BigDecimal.ZERO);
            assertThrows(IllegalArgumentException.class, () -> refundService.refundFullSale(
                    administrator, freeSale.number(), "Billet gratuit"));

            var unpaidSale = createSale(administratorId, event.id(), category.id(), 1, new BigDecimal("1750.00"));
            setPaymentAmount(unpaidSale.id(), new BigDecimal("1.00"));
            assertThrows(IllegalArgumentException.class, () -> refundService.refundFullSale(
                    administrator, unpaidSale.number(), "Paiement incohérent"));
            assertEquals(0, count("SELECT COUNT(*) FROM remboursements WHERE vente_id = ?", unpaidSale.id()));

            setEventStart(event.id(), LocalDateTime.now().minusMinutes(1));
            assertThrows(IllegalArgumentException.class, () -> refundService.refundFullSale(
                    administrator, afterEventSale.number(), "Événement commencé"));
            assertEquals("PAYEE", saleStatus(afterEventSale.id()));
        } finally {
            cleanup(administratorId, event, room, venue);
        }
    }

    private com.mycompany.tike.model.SaleResult createSale(
            long sellerId, long eventId, long categoryId, int quantity, BigDecimal total) throws SQLException {
        return salesDao.createSale(new SaleRequest(
                eventId, null, List.of(new SaleLineRequest(categoryId, quantity)), total,
                PaymentMethod.ESPECES, null), sellerId);
    }

    private static long createAdministrator(String suffix) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
                     VALUES (?, ?, 'test-only-not-a-password', 'ADMIN', TRUE)
                     RETURNING id
                     """)) {
            statement.setString(1, "Administrateur intégration " + suffix);
            statement.setString(2, "it.refund." + suffix);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String ticketCode(long saleId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT code_unique FROM billets WHERE vente_id = ?")) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1, UUID.class).toString();
            }
        }
    }

    private static int count(String sql, long saleId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static String saleStatus(long saleId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT statut FROM ventes WHERE id = ?")) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static void setEventStart(long eventId, LocalDateTime startsAt) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE evenements SET debut = ? WHERE id = ?")) {
            statement.setObject(1, startsAt);
            statement.setLong(2, eventId);
            statement.executeUpdate();
        }
    }

    private static void setPaymentAmount(long saleId, BigDecimal amount) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE paiements SET montant = ? WHERE vente_id = ?")) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, saleId);
            statement.executeUpdate();
        }
    }

    private static void cleanup(long administratorId, Event event, Room room, Venue venue)
            throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (event != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM controles_acces WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM remboursements WHERE vente_id IN (SELECT id FROM ventes WHERE evenement_id = ?)")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM billets WHERE evenement_id = ?")) {
                    statement.setLong(1, event.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM paiements WHERE vente_id IN (SELECT id FROM ventes WHERE evenement_id = ?)")) {
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
                    "DELETE FROM journal_activites WHERE utilisateur_id = ?")) {
                statement.setLong(1, administratorId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM utilisateurs WHERE id = ?")) {
                statement.setLong(1, administratorId);
                statement.executeUpdate();
            }
        }
    }
}
