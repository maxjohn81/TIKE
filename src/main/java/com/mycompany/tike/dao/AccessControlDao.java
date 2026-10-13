package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.AccessControlEvent;
import com.mycompany.tike.model.AccessControlOutcome;
import com.mycompany.tike.model.AccessControlResult;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.SaleHistoryItem;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccessControlDao {
    public List<AccessControlEvent> findEvents() throws SQLException {
        String sql = """
                SELECT id, nom, debut, statut
                FROM evenements
                WHERE statut IN ('PROGRAMME', 'EN_VENTE', 'COMPLET')
                ORDER BY debut, nom, id
                """;
        List<AccessControlEvent> events = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Timestamp startsAt = result.getTimestamp("debut");
                events.add(new AccessControlEvent(
                        result.getLong("id"),
                        result.getString("nom"),
                        startsAt == null ? null : startsAt.toLocalDateTime(),
                        EventStatus.valueOf(result.getString("statut"))));
            }
        }
        return events;
    }

    public List<SaleHistoryItem> findSalesForEvent(long eventId) throws SQLException {
        List<SaleHistoryItem> sales = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT v.numero_vente, e.nom AS evenement,
                            COALESCE(cl.nom_complet, 'Client non renseigné') AS client,
                            v.date_vente, v.montant_total, v.statut, COUNT(b.id) AS nombre_billets
                     FROM ventes v
                     JOIN evenements e ON e.id = v.evenement_id
                     LEFT JOIN clients cl ON cl.id = v.client_id
                     LEFT JOIN billets b ON b.vente_id = v.id
                     WHERE v.evenement_id = ?
                     GROUP BY v.id, e.nom, cl.nom_complet
                     ORDER BY v.date_vente DESC, v.id DESC
                     """)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp date = result.getTimestamp("date_vente");
                    sales.add(new SaleHistoryItem(
                            result.getString("numero_vente"),
                            result.getString("evenement"),
                            result.getString("client"),
                            date == null ? null : date.toLocalDateTime(),
                            result.getBigDecimal("montant_total"),
                            result.getString("statut"),
                            result.getInt("nombre_billets")));
                }
            }
        }
        return sales;
    }

    public List<TicketChoice> findTicketsForSale(long eventId, String saleNumber) throws SQLException {
        List<TicketChoice> tickets = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT b.code_unique, cb.nom AS categorie, b.statut
                     FROM billets b
                     JOIN ventes v ON v.id = b.vente_id
                     JOIN categories_billets cb ON cb.id = b.categorie_id
                     WHERE b.evenement_id = ? AND v.numero_vente = ?
                     ORDER BY b.id
                     """)) {
            statement.setLong(1, eventId);
            statement.setString(2, saleNumber);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tickets.add(new TicketChoice(
                            result.getString("code_unique"),
                            result.getString("categorie"),
                            result.getString("statut")));
                }
            }
        }
        return tickets;
    }

    public AccessControlResult check(long eventId, UUID ticketCode, long controllerId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String selectedEventName = findEventName(connection, eventId);
                if (selectedEventName == null) {
                    throw new IllegalArgumentException("L'événement sélectionné n'existe plus.");
                }
                if (ticketCode == null) {
                    AccessControlResult result = recordOutcome(connection, eventId, controllerId, null,
                            AccessControlOutcome.NON_TROUVE, selectedEventName, null, null, null, null);
                    ActivityLogDao.record(connection, controllerId, "CONTROLE_ACCES", "EVENEMENT", eventId,
                            "Résultat : " + result.outcome().name());
                    connection.commit();
                    return result;
                }

                TicketRow ticket = findTicketForUpdate(connection, ticketCode);
                if (ticket == null) {
                    AccessControlResult result = recordOutcome(connection, eventId, controllerId, null,
                            AccessControlOutcome.NON_TROUVE, selectedEventName, null, null, null, null);
                    ActivityLogDao.record(connection, controllerId, "CONTROLE_ACCES", "EVENEMENT", eventId,
                            "Résultat : " + result.outcome().name());
                    connection.commit();
                    return result;
                }

                AccessControlOutcome outcome;
                if (ticket.eventId() != eventId) {
                    outcome = AccessControlOutcome.MAUVAIS_EVENEMENT;
                } else if (ticket.status().equals("ANNULE") || ticket.status().equals("REMBOURSE")
                        || ticket.saleStatus().equals("ANNULEE") || ticket.saleStatus().equals("REMBOURSEE")) {
                    outcome = AccessControlOutcome.ANNULE;
                } else if (ticket.status().equals("UTILISE")) {
                    outcome = AccessControlOutcome.DEJA_UTILISE;
                } else if (!ticket.saleStatus().equals("PAYEE")
                        || (ticket.saleTotal().signum() > 0
                                && ticket.paidAmount().compareTo(ticket.saleTotal()) < 0)) {
                    outcome = AccessControlOutcome.NON_PAYE;
                } else {
                    outcome = AccessControlOutcome.VALIDE;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE billets
                            SET statut = 'UTILISE', utilise_le = datetime('now', 'localtime'), controle_par = ?
                            WHERE id = ? AND statut = 'VALIDE'
                            """)) {
                        update.setLong(1, controllerId);
                        update.setLong(2, ticket.id());
                        if (update.executeUpdate() != 1) {
                            throw new SQLException("Le billet a changé de statut pendant sa validation.");
                        }
                    }
                }
                AccessControlResult result = recordOutcome(
                        connection, eventId, controllerId, ticket.id(), outcome,
                        selectedEventName, ticket.eventName(), ticket.categoryName(),
                        ticket.customerName(), ticket.code());
                ActivityLogDao.record(connection, controllerId, "CONTROLE_ACCES", "BILLET", ticket.id(),
                        "Événement contrôlé " + eventId + " ; résultat : " + outcome.name());
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    private static String findEventName(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT nom FROM evenements WHERE id = ?")) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("nom") : null;
            }
        }
    }

    private static TicketRow findTicketForUpdate(Connection connection, UUID ticketCode) throws SQLException {
        String sql = """
                SELECT b.id, b.evenement_id, b.code_unique, b.statut,
                       v.statut AS vente_statut, v.montant_total,
                       COALESCE((
                           SELECT SUM(p.montant)
                           FROM paiements p
                           WHERE p.vente_id = v.id AND p.statut = 'PAYE'
                       ), 0) AS montant_paye,
                       e.nom AS evenement, c.nom AS categorie,
                       COALESCE(cl.nom_complet, 'Client non renseigné') AS client
                FROM billets b
                JOIN ventes v ON v.id = b.vente_id
                JOIN evenements e ON e.id = b.evenement_id
                JOIN categories_billets c ON c.id = b.categorie_id
                LEFT JOIN clients cl ON cl.id = v.client_id
                WHERE b.code_unique = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticketCode.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new TicketRow(
                        result.getLong("id"),
                        result.getLong("evenement_id"),
                        result.getString("code_unique"),
                        result.getString("statut"),
                        result.getString("vente_statut"),
                        result.getBigDecimal("montant_total"),
                        result.getBigDecimal("montant_paye"),
                        result.getString("evenement"),
                        result.getString("categorie"),
                        result.getString("client"));
            }
        }
    }

    private static AccessControlResult recordOutcome(
            Connection connection, long eventId, long controllerId, Long ticketId,
            AccessControlOutcome outcome, String eventName, String ticketEventName,
            String categoryName, String customerName, String ticketCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO controles_acces
                    (billet_id, controleur_id, evenement_id, resultat)
                VALUES (?, ?, ?, ?)
                RETURNING controle_le
                """)) {
            if (ticketId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, ticketId);
            }
            statement.setLong(2, controllerId);
            statement.setLong(3, eventId);
            statement.setString(4, outcome.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("L'historique du contrôle n'a pas pu être enregistré.");
                }
                Timestamp checkedAt = result.getTimestamp("controle_le");
                return new AccessControlResult(
                        outcome, eventName, ticketEventName, categoryName,
                        customerName, ticketCode,
                        checkedAt == null ? null : checkedAt.toLocalDateTime());
            }
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private record TicketRow(
            long id,
            long eventId,
            String code,
            String status,
            String saleStatus,
            BigDecimal saleTotal,
            BigDecimal paidAmount,
            String eventName,
            String categoryName,
            String customerName) {
    }

    public record TicketChoice(String code, String categoryName, String status) {
        @Override
        public String toString() {
            return categoryName + " — " + status + " — " + code;
        }
    }
}
