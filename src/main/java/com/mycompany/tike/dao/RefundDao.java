package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.RefundResult;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RefundDao {
    public RefundResult refundFullSale(String saleNumber, String reason, long administratorId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SaleForRefund sale = lockSale(connection, saleNumber);
                if (sale == null) {
                    throw new IllegalArgumentException("Aucune vente ne correspond à ce numéro.");
                }
                if (!sale.status().equals("PAYEE")) {
                    throw new IllegalArgumentException("Seule une vente payée et non remboursée peut être remboursée.");
                }
                if (!sale.eventIsUpcoming()) {
                    throw new IllegalArgumentException("Le remboursement est impossible après le début de l'événement.");
                }
                if (sale.total().signum() <= 0) {
                    throw new IllegalArgumentException("Une vente gratuite ne peut pas faire l'objet d'un remboursement.");
                }

                List<Long> ticketIds = lockUnusedTickets(connection, sale.id());
                List<Long> paidPaymentIds = lockAndValidatePaidPayments(connection, sale.id(), sale.total());
                if (ticketIds.isEmpty()) {
                    throw new IllegalArgumentException("Cette vente ne contient aucun billet à rembourser.");
                }

                String refundNumber = "RB-" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 16).toUpperCase(java.util.Locale.ROOT);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO remboursements
                            (vente_id, numero_remboursement, montant, motif, effectue_par)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, sale.id());
                    statement.setString(2, refundNumber);
                    statement.setBigDecimal(3, sale.total());
                    statement.setString(4, reason);
                    statement.setLong(5, administratorId);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE paiements
                        SET statut = 'REMBOURSE'
                        WHERE vente_id = ? AND statut = 'PAYE'
                        """)) {
                    statement.setLong(1, sale.id());
                    if (statement.executeUpdate() != paidPaymentIds.size()) {
                        throw new SQLException("Le statut des paiements a changé pendant le remboursement.");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE billets
                        SET statut = 'REMBOURSE'
                        WHERE vente_id = ? AND statut = 'VALIDE'
                        """)) {
                    statement.setLong(1, sale.id());
                    if (statement.executeUpdate() != ticketIds.size()) {
                        throw new SQLException("Le statut des billets a changé pendant le remboursement.");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE ventes SET statut = 'REMBOURSEE'
                        WHERE id = ? AND statut = 'PAYEE'
                        """)) {
                    statement.setLong(1, sale.id());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Le statut de la vente a changé pendant le remboursement.");
                    }
                }
                ActivityLogDao.record(
                        connection, administratorId, "REMBOURSEMENT_ENREGISTRE", "REMBOURSEMENT",
                        refundId(connection, refundNumber),
                        "Remboursement " + refundNumber + " de la vente " + sale.number()
                                + " ; montant " + sale.total().toPlainString() + " ; motif : " + reason);
                connection.commit();
                return new RefundResult(sale.number(), refundNumber, sale.total(), ticketIds.size());
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    private static long refundId(Connection connection, String refundNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM remboursements WHERE numero_remboursement = ?")) {
            statement.setString(1, refundNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Le remboursement enregistré est introuvable.");
                }
                return result.getLong(1);
            }
        }
    }

    private static SaleForRefund lockSale(Connection connection, String saleNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.id, v.numero_vente, v.montant_total, v.statut,
                       (e.debut > datetime('now', 'localtime')) AS evenement_a_venir
                FROM ventes v
                JOIN evenements e ON e.id = v.evenement_id
                WHERE v.numero_vente = ?
                """)) {
            statement.setString(1, saleNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SaleForRefund(
                        result.getLong("id"),
                        result.getString("numero_vente"),
                        result.getBigDecimal("montant_total"),
                        result.getString("statut"),
                        result.getBoolean("evenement_a_venir"));
            }
        }
    }

    private static List<Long> lockUnusedTickets(Connection connection, long saleId) throws SQLException {
        List<Long> ticketIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, statut
                FROM billets
                WHERE vente_id = ?
                ORDER BY id
                """)) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString("statut");
                    if (!"VALIDE".equals(status)) {
                        throw new IllegalArgumentException(
                                "Remboursement refusé : au moins un billet n'est plus valide (il a pu être utilisé ou annulé).");
                    }
                    ticketIds.add(result.getLong("id"));
                }
            }
        }
        return ticketIds;
    }

    private static List<Long> lockAndValidatePaidPayments(
            Connection connection, long saleId, BigDecimal saleTotal) throws SQLException {
        List<Long> paymentIds = new ArrayList<>();
        BigDecimal paidTotal = BigDecimal.ZERO;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, montant
                FROM paiements
                WHERE vente_id = ? AND statut = 'PAYE'
                ORDER BY id
                """)) {
            statement.setLong(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    paymentIds.add(result.getLong("id"));
                    paidTotal = paidTotal.add(result.getBigDecimal("montant"));
                }
            }
        }
        if (paymentIds.isEmpty() || paidTotal.compareTo(saleTotal) != 0) {
            throw new IllegalArgumentException(
                    "Remboursement refusé : les paiements enregistrés ne correspondent pas au total de la vente.");
        }
        return paymentIds;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private record SaleForRefund(long id, String number, BigDecimal total, String status, boolean eventIsUpcoming) {
    }
}
