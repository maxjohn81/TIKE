package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.SaleHistoryItem;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.Ticket;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SalesDao {
    public List<Client> findClients(String search) throws SQLException {
        String sql = """
                SELECT id, nom_complet, telephone, email
                FROM clients
                WHERE LOWER(nom_complet) LIKE LOWER(?)
                   OR LOWER(COALESCE(telephone, '')) LIKE LOWER(?)
                   OR LOWER(COALESCE(email, '')) LIKE LOWER(?)
                ORDER BY nom_complet, id
                """;
        String pattern = "%" + (search == null ? "" : search.trim()) + "%";
        List<Client> clients = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    clients.add(mapClient(result));
                }
            }
        }
        return clients;
    }

    public Client saveClient(Client client) throws SQLException {
        String sql = client.id() == 0
                ? """
                  INSERT INTO clients (nom_complet, telephone, email) VALUES (?, ?, ?)
                  RETURNING id, nom_complet, telephone, email
                  """
                : """
                  UPDATE clients SET nom_complet = ?, telephone = ?, email = ?
                  WHERE id = ?
                  RETURNING id, nom_complet, telephone, email
                  """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, client.fullName());
            statement.setString(2, client.phone());
            statement.setString(3, client.email());
            if (client.id() != 0) {
                statement.setLong(4, client.id());
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Ce client n'existe plus.");
                }
                return mapClient(result);
            }
        }
    }

    public List<SaleHistoryItem> findClientHistory(long clientId) throws SQLException {
        String sql = """
                SELECT v.numero_vente, e.nom AS evenement,
                       COALESCE(c.nom_complet, 'Client non renseigné') AS client,
                       v.date_vente, v.montant_total, v.statut,
                       COUNT(b.id) AS nombre_billets
                FROM ventes v
                JOIN evenements e ON e.id = v.evenement_id
                LEFT JOIN clients c ON c.id = v.client_id
                LEFT JOIN billets b ON b.vente_id = v.id
                WHERE v.client_id = ?
                GROUP BY v.id, e.nom, c.nom_complet
                ORDER BY v.date_vente DESC, v.id DESC
                """;
        List<SaleHistoryItem> history = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, clientId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp date = result.getTimestamp("date_vente");
                    history.add(new SaleHistoryItem(
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
        return history;
    }

    public List<SaleHistoryItem> findAllSales() throws SQLException {
        String sql = """
                SELECT v.numero_vente, e.nom AS evenement,
                       COALESCE(c.nom_complet, 'Client non renseigné') AS client,
                       v.date_vente, v.montant_total, v.statut,
                       COUNT(b.id) AS nombre_billets
                FROM ventes v
                JOIN evenements e ON e.id = v.evenement_id
                LEFT JOIN clients c ON c.id = v.client_id
                LEFT JOIN billets b ON b.vente_id = v.id
                GROUP BY v.id, e.nom, c.nom_complet
                ORDER BY v.date_vente DESC, v.id DESC
                """;
        List<SaleHistoryItem> sales = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
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
        return sales;
    }

    public List<Ticket> findPaidSaleTickets(String saleNumber) throws SQLException {
        String sql = """
                SELECT b.id, v.numero_vente, e.nom AS evenement, e.debut,
                       COALESCE(l.nom || ' — ' || s.nom, l.nom, s.nom, 'Lieu non renseigné') AS lieu,
                       c.nom AS categorie, COALESCE(cl.nom_complet, 'Client non renseigné') AS client,
                       vl.prix_unitaire, b.code_unique, b.statut
                FROM ventes v
                JOIN billets b ON b.vente_id = v.id
                JOIN evenements e ON e.id = b.evenement_id
                JOIN categories_billets c ON c.id = b.categorie_id
                JOIN vente_lignes vl ON vl.vente_id = v.id AND vl.categorie_id = c.id
                LEFT JOIN clients cl ON cl.id = v.client_id
                LEFT JOIN salles s ON s.id = e.salle_id
                LEFT JOIN lieux l ON l.id = s.lieu_id
                WHERE v.numero_vente = ? AND v.statut = 'PAYEE'
                ORDER BY b.id
                """;
        List<Ticket> tickets = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleNumber);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp eventStart = result.getTimestamp("debut");
                    tickets.add(new Ticket(
                            result.getLong("id"),
                            result.getString("numero_vente"),
                            result.getString("evenement"),
                            eventStart == null ? null : eventStart.toLocalDateTime(),
                            result.getString("lieu"),
                            result.getString("categorie"),
                            result.getString("client"),
                            result.getBigDecimal("prix_unitaire"),
                            result.getString("code_unique"),
                            result.getString("statut")));
                }
            }
        }
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucun billet associé à cette vente payée. Vérifiez le numéro de vente.");
        }
        return tickets;
    }

    public SaleResult createSale(SaleRequest request, long sellerId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockSellableEvent(connection, request.eventId());
                ReservationDao.expireForEvent(connection, request.eventId());
                SaleResult sale = createSaleInTransaction(connection, request, sellerId, null);
                connection.commit();
                return sale;
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    static SaleResult createSaleInTransaction(
            Connection connection, SaleRequest request, long sellerId, Long reservationToExclude)
            throws SQLException {
        if (request.clientId() != null) {
            ensureClientExists(connection, request.clientId());
        }

        List<PricedLine> pricedLines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int ticketCount = 0;
        for (SaleLineRequest line : request.lines()) {
            PricedLine pricedLine = lockAndPriceCategory(
                    connection, request.eventId(), line, reservationToExclude);
            pricedLines.add(pricedLine);
            total = total.add(pricedLine.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            ticketCount = Math.addExact(ticketCount, line.quantity());
        }
        if (total.precision() > 12) {
            throw new IllegalArgumentException("Le montant total dépasse la limite autorisée.");
        }
        if (request.expectedTotal() == null || request.expectedTotal().compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    "Un tarif a changé depuis la préparation du panier. Vérifiez le nouveau total avant de réessayer.");
        }

        String saleNumber = "VT-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(java.util.Locale.ROOT);
        long saleId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ventes
                    (client_id, evenement_id, vendeur_id, numero_vente, montant_total, statut)
                VALUES (?, ?, ?, ?, ?, 'PAYEE')
                RETURNING id
                """)) {
            if (request.clientId() == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, request.clientId());
            }
            statement.setLong(2, request.eventId());
            statement.setLong(3, sellerId);
            statement.setString(4, saleNumber);
            statement.setBigDecimal(5, total);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                saleId = result.getLong(1);
            }
        }

        for (PricedLine line : pricedLines) {
            BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(line.request().quantity()));
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vente_lignes
                        (vente_id, evenement_id, categorie_id, quantite, prix_unitaire, montant_ligne)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, saleId);
                statement.setLong(2, request.eventId());
                statement.setLong(3, line.request().categoryId());
                statement.setInt(4, line.request().quantity());
                statement.setBigDecimal(5, line.unitPrice());
                statement.setBigDecimal(6, lineTotal);
                statement.executeUpdate();
            }
            addTickets(connection, saleId, request.eventId(), line.request(), line.request().quantity());
        }

        if (total.signum() > 0) {
            try (PreparedStatement payment = connection.prepareStatement("""
                    INSERT INTO paiements
                        (vente_id, montant, methode, reference_paiement, statut, enregistre_par)
                    VALUES (?, ?, ?, ?, 'PAYE', ?)
                    """)) {
                payment.setLong(1, saleId);
                payment.setBigDecimal(2, total);
                payment.setString(3, request.paymentMethod().name());
                payment.setString(4, request.paymentReference());
                payment.setLong(5, sellerId);
                payment.executeUpdate();
            }
        }
        ActivityLogDao.record(
                connection, sellerId, "VENTE_CREEE", "VENTE", saleId,
                "Vente " + saleNumber + " ; montant " + total.toPlainString()
                        + " ; billets " + ticketCount);
        return new SaleResult(saleId, saleNumber, total, ticketCount);
    }

    static void lockSellableEvent(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT statut,
                       (ouverture_vente IS NULL OR ouverture_vente <= datetime('now', 'localtime')) AS vente_ouverte,
                       (fermeture_vente IS NULL OR fermeture_vente > datetime('now', 'localtime')) AS vente_non_fermee,
                       (debut > datetime('now', 'localtime')) AS evenement_a_venir
                FROM evenements
                WHERE id = ?
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("L'événement sélectionné n'existe plus.");
                }
                if (!"EN_VENTE".equals(result.getString("statut"))) {
                    throw new IllegalArgumentException("La vente est autorisée uniquement pour un événement « En vente ».");
                }
                if (!result.getBoolean("vente_ouverte")) {
                    throw new IllegalArgumentException("La période de vente de cet événement n'a pas encore commencé.");
                }
                if (!result.getBoolean("vente_non_fermee")) {
                    throw new IllegalArgumentException("La période de vente de cet événement est terminée.");
                }
                if (!result.getBoolean("evenement_a_venir")) {
                    throw new IllegalArgumentException("L'événement a commencé : les ventes sont fermées.");
                }
            }
        }
    }

    private static void ensureClientExists(Connection connection, long clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM clients WHERE id = ?")) {
            statement.setLong(1, clientId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Le client sélectionné n'existe plus.");
                }
            }
        }
    }

    private static PricedLine lockAndPriceCategory(
            Connection connection, long eventId, SaleLineRequest line, Long reservationToExclude) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT prix, quantite_totale
                FROM categories_billets
                WHERE id = ? AND evenement_id = ?
                """)) {
            statement.setLong(1, line.categoryId());
            statement.setLong(2, eventId);
            BigDecimal price;
            int total;
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Une catégorie sélectionnée n'appartient pas à cet événement.");
                }
                price = result.getBigDecimal("prix");
                total = result.getInt("quantite_totale");
            }
            long sold = countActiveSoldTickets(connection, line.categoryId());
            long reserved = ReservationDao.countActiveReservedTickets(
                    connection, line.categoryId(), reservationToExclude);
            long available = total - sold - reserved;
            if (line.quantity() > available) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour la catégorie sélectionnée. Disponibles : "
                                + Math.max(available, 0) + ".");
            }
            return new PricedLine(line, price);
        }
    }

    static long countActiveSoldTickets(Connection connection, long categoryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM billets b
                JOIN ventes v ON v.id = b.vente_id
                WHERE b.categorie_id = ?
                  AND v.statut = 'PAYEE'
                  AND b.statut NOT IN ('ANNULE', 'REMBOURSE')
                """)) {
            statement.setLong(1, categoryId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void addTickets(
            Connection connection, long saleId, long eventId, SaleLineRequest line, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO billets (vente_id, evenement_id, categorie_id, code_unique, statut)
                VALUES (?, ?, ?, ?, 'VALIDE')
                """)) {
            for (int i = 0; i < quantity; i++) {
                statement.setLong(1, saleId);
                statement.setLong(2, eventId);
                statement.setLong(3, line.categoryId());
                statement.setString(4, UUID.randomUUID().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Client mapClient(ResultSet result) throws SQLException {
        return new Client(
                result.getLong("id"),
                result.getString("nom_complet"),
                result.getString("telephone"),
                result.getString("email"));
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private record PricedLine(SaleLineRequest request, BigDecimal unitPrice) {
    }
}
