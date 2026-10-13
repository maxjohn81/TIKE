package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.Reservation;
import com.mycompany.tike.model.ReservationLine;
import com.mycompany.tike.model.ReservationLineRequest;
import com.mycompany.tike.model.ReservationRequest;
import com.mycompany.tike.model.ReservationStatus;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.PaymentMethod;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ReservationDao {
    public static final int DEFAULT_EXPIRATION_HOURS = 24;

    public Reservation create(ReservationRequest request, long actorId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockReservableEvent(connection, request.eventId());
                expireForEvent(connection, request.eventId());
                if (request.clientId() != null) {
                    ensureClientExists(connection, request.clientId());
                }
                List<ReservationLine> lines = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (ReservationLineRequest line : request.lines()) {
                    lines.add(lockAndPriceCategory(connection, request.eventId(), line));
                    ReservationLine priced = lines.get(lines.size() - 1);
                    total = total.add(priced.lineTotal());
                }
                if (total.precision() > 12) {
                    throw new IllegalArgumentException("Le montant total dépasse la limite autorisée.");
                }

                String number = "RS-" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 16).toUpperCase(java.util.Locale.ROOT);
                long reservationId;
                Timestamp createdAt;
                Timestamp expiresAt;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO reservations
                            (numero_reservation, evenement_id, client_id, cree_par, cree_le, expire_le, statut)
                        VALUES (?, ?, ?, ?, datetime('now', 'localtime'),
                                MIN(datetime('now', 'localtime', '+' || ? || ' hours'),
                                      (SELECT debut FROM evenements WHERE id = ?)),
                                'EN_ATTENTE')
                        RETURNING id, cree_le, expire_le
                        """)) {
                    statement.setString(1, number);
                    statement.setLong(2, request.eventId());
                    if (request.clientId() == null) {
                        statement.setNull(3, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(3, request.clientId());
                    }
                    statement.setLong(4, actorId);
                    statement.setInt(5, DEFAULT_EXPIRATION_HOURS);
                    statement.setLong(6, request.eventId());
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        reservationId = result.getLong("id");
                        createdAt = result.getTimestamp("cree_le");
                        expiresAt = result.getTimestamp("expire_le");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO reservation_lignes
                            (reservation_id, evenement_id, categorie_id, quantite, prix_unitaire)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    for (ReservationLine line : lines) {
                        statement.setLong(1, reservationId);
                        statement.setLong(2, request.eventId());
                        statement.setLong(3, line.categoryId());
                        statement.setInt(4, line.quantity());
                        statement.setBigDecimal(5, line.unitPrice());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                ActivityLogDao.record(connection, actorId, "RESERVATION_CREEE", "RESERVATION", reservationId,
                        "Réservation " + number + " ; événement " + request.eventId());
                connection.commit();
                return new Reservation(
                        reservationId, number, request.eventId(), null, request.clientId(), null,
                        createdAt.toLocalDateTime(), expiresAt.toLocalDateTime(),
                        ReservationStatus.EN_ATTENTE, total, lines);
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    public List<Reservation> findAll(String search, ReservationStatus status) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                expireAll(connection);
                String sql = """
                        SELECT r.id, r.numero_reservation, r.evenement_id, e.nom AS evenement,
                               r.client_id, c.nom_complet AS client, r.cree_le, r.expire_le, r.statut,
                               COALESCE(SUM(rl.quantite * rl.prix_unitaire), 0) AS total
                        FROM reservations r
                        JOIN evenements e ON e.id = r.evenement_id
                        LEFT JOIN clients c ON c.id = r.client_id
                        LEFT JOIN reservation_lignes rl ON rl.reservation_id = r.id
                        WHERE (LOWER(r.numero_reservation) LIKE LOWER(?)
                               OR LOWER(e.nom) LIKE LOWER(?)
                               OR LOWER(COALESCE(c.nom_complet, '')) LIKE LOWER(?))
                          AND (? IS NULL OR r.statut = ?)
                        GROUP BY r.id, e.nom, c.nom_complet
                        ORDER BY r.cree_le DESC, r.id DESC
                        """;
                String pattern = "%" + (search == null ? "" : search.trim()) + "%";
                List<ReservationHeader> headers = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, pattern);
                    statement.setString(2, pattern);
                    statement.setString(3, pattern);
                    statement.setString(4, status == null ? null : status.name());
                    statement.setString(5, status == null ? null : status.name());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            long clientValue = result.getLong("client_id");
                            Long clientId = result.wasNull() ? null : clientValue;
                            Timestamp created = result.getTimestamp("cree_le");
                            Timestamp expires = result.getTimestamp("expire_le");
                            headers.add(new ReservationHeader(
                                    result.getLong("id"),
                                    result.getString("numero_reservation"),
                                    result.getLong("evenement_id"),
                                    result.getString("evenement"),
                                    clientId,
                                    result.getString("client"),
                                    created.toLocalDateTime(),
                                    expires.toLocalDateTime(),
                                    ReservationStatus.valueOf(result.getString("statut")),
                                    result.getBigDecimal("total")));
                        }
                    }
                }
                List<Reservation> reservations = new ArrayList<>();
                for (ReservationHeader header : headers) {
                    reservations.add(new Reservation(
                            header.id(), header.number(), header.eventId(), header.eventName(),
                            header.clientId(), header.clientName(), header.createdAt(), header.expiresAt(),
                            header.status(), header.total(), findLines(connection, header.id())));
                }
                connection.commit();
                return reservations;
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    public void cancel(long reservationId, long actorId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long eventId = findEventId(connection, reservationId);
                lockEvent(connection, eventId);
                expireForEvent(connection, eventId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE reservations SET statut = 'ANNULEE'
                        WHERE id = ? AND statut = 'EN_ATTENTE'
                        RETURNING id
                        """)) {
                    statement.setLong(1, reservationId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException(
                                    "Seule une réservation en attente peut être annulée.");
                        }
                    }
                }
                ActivityLogDao.record(connection, actorId, "RESERVATION_ANNULEE", "RESERVATION",
                        reservationId, null);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    public SaleResult convert(long reservationId, PaymentMethod paymentMethod, String reference, long actorId)
            throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long eventId = findEventId(connection, reservationId);
                SalesDao.lockSellableEvent(connection, eventId);
                expireForEvent(connection, eventId);
                Reservation reservation = findPendingReservationForUpdate(connection, reservationId);
                if (reservation == null) {
                    throw new IllegalArgumentException(
                            "La réservation n'est plus en attente, a expiré ou a déjà été convertie.");
                }
                List<SaleLineRequest> saleLines = reservation.lines().stream()
                        .map(line -> new SaleLineRequest(line.categoryId(), line.quantity()))
                        .toList();
                SaleRequest saleRequest = new SaleRequest(
                        eventId, reservation.clientId(), saleLines, reservation.total(), paymentMethod, reference);
                SaleResult sale = SalesDao.createSaleInTransaction(
                        connection, saleRequest, actorId, reservationId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE reservations
                        SET statut = 'CONFIRMEE', vente_id = ?
                        WHERE id = ? AND statut = 'EN_ATTENTE' AND expire_le > datetime('now', 'localtime')
                        """)) {
                    statement.setLong(1, sale.id());
                    statement.setLong(2, reservationId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "La réservation a expiré ou a été modifiée avant la conversion.");
                    }
                }
                ActivityLogDao.record(connection, actorId, "RESERVATION_CONVERTIE", "RESERVATION",
                        reservationId, "Vente " + sale.number());
                connection.commit();
                return sale;
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        }
    }

    public void expirePending() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE reservations SET statut = 'EXPIREE'
                     WHERE statut = 'EN_ATTENTE' AND expire_le <= datetime('now', 'localtime')
                     """)) {
            statement.executeUpdate();
        }
    }

    static void expireForEvent(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE reservations SET statut = 'EXPIREE'
                WHERE evenement_id = ? AND statut = 'EN_ATTENTE' AND expire_le <= datetime('now', 'localtime')
                """)) {
            statement.setLong(1, eventId);
            statement.executeUpdate();
        }
    }

    static long countActiveReservedTickets(Connection connection, long categoryId, Long excludedReservationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(rl.quantite), 0)
                FROM reservation_lignes rl
                JOIN reservations r ON r.id = rl.reservation_id
                WHERE rl.categorie_id = ?
                  AND r.statut = 'EN_ATTENTE'
                  AND r.expire_le > datetime('now', 'localtime')
                  AND (? IS NULL OR r.id <> ?)
                """)) {
            statement.setLong(1, categoryId);
            if (excludedReservationId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
                statement.setNull(3, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, excludedReservationId);
                statement.setLong(3, excludedReservationId);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static ReservationLine lockAndPriceCategory(
            Connection connection, long eventId, ReservationLineRequest line) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT nom, prix, quantite_totale
                FROM categories_billets
                WHERE id = ? AND evenement_id = ?
                """)) {
            statement.setLong(1, line.categoryId());
            statement.setLong(2, eventId);
            String name;
            BigDecimal price;
            int total;
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Une catégorie sélectionnée n'appartient pas à cet événement.");
                }
                name = result.getString("nom");
                price = result.getBigDecimal("prix");
                total = result.getInt("quantite_totale");
            }
            long sold = SalesDao.countActiveSoldTickets(connection, line.categoryId());
            long reserved = countActiveReservedTickets(connection, line.categoryId(), null);
            long available = total - sold - reserved;
            if (line.quantity() > available) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour la catégorie « " + name + " ». Disponibles : "
                                + Math.max(available, 0) + ".");
            }
            return new ReservationLine(line.categoryId(), name, line.quantity(), price);
        }
    }

    private static List<ReservationLine> findLines(Connection connection, long reservationId) throws SQLException {
        List<ReservationLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT rl.categorie_id, c.nom, rl.quantite, rl.prix_unitaire
                FROM reservation_lignes rl
                JOIN categories_billets c ON c.id = rl.categorie_id
                WHERE rl.reservation_id = ?
                ORDER BY c.nom
                """)) {
            statement.setLong(1, reservationId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    lines.add(new ReservationLine(
                            result.getLong("categorie_id"),
                            result.getString("nom"),
                            result.getInt("quantite"),
                            result.getBigDecimal("prix_unitaire")));
                }
            }
        }
        return lines;
    }

    private static Reservation findPendingReservationForUpdate(Connection connection, long reservationId)
            throws SQLException {
        String number;
        long eventId;
        Long clientId;
        java.time.LocalDateTime createdAt;
        java.time.LocalDateTime expiresAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, numero_reservation, evenement_id, client_id, cree_le, expire_le
                FROM reservations
                WHERE id = ? AND statut = 'EN_ATTENTE' AND expire_le > datetime('now', 'localtime')
                """)) {
            statement.setLong(1, reservationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long clientValue = result.getLong("client_id");
                clientId = result.wasNull() ? null : clientValue;
                number = result.getString("numero_reservation");
                eventId = result.getLong("evenement_id");
                createdAt = result.getTimestamp("cree_le").toLocalDateTime();
                expiresAt = result.getTimestamp("expire_le").toLocalDateTime();
            }
        }
        return new Reservation(
                reservationId, number, eventId, null, clientId, null, createdAt, expiresAt,
                ReservationStatus.EN_ATTENTE, totalLines(connection, reservationId),
                findLines(connection, reservationId));
    }

    private static BigDecimal totalLines(Connection connection, long reservationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(quantite * prix_unitaire), 0)
                FROM reservation_lignes
                WHERE reservation_id = ?
                """)) {
            statement.setLong(1, reservationId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal(1);
            }
        }
    }

    private static void lockReservableEvent(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT statut, debut
                FROM evenements
                WHERE id = ?
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("L'événement sélectionné n'existe plus.");
                }
                String status = result.getString("statut");
                if (!"PROGRAMME".equals(status) && !"EN_VENTE".equals(status)) {
                    throw new IllegalArgumentException("Une réservation est impossible pour cet événement.");
                }
                Timestamp startsAt = result.getTimestamp("debut");
                if (startsAt == null || !startsAt.toInstant().isAfter(java.time.Instant.now())) {
                    throw new IllegalArgumentException("Une réservation est impossible après le début de l'événement.");
                }
            }
        }
    }

    private static void lockEvent(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM evenements WHERE id = ?")) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("L'événement lié à cette réservation n'existe plus.");
                }
            }
        }
    }

    private static long findEventId(Connection connection, long reservationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT evenement_id FROM reservations WHERE id = ?")) {
            statement.setLong(1, reservationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Cette réservation n'existe plus.");
                }
                return result.getLong(1);
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

    private record ReservationHeader(
            long id,
            String number,
            long eventId,
            String eventName,
            Long clientId,
            String clientName,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime expiresAt,
            ReservationStatus status,
            BigDecimal total) {
    }

    private static void expireAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE reservations SET statut = 'EXPIREE'
                WHERE statut = 'EN_ATTENTE' AND expire_le <= datetime('now', 'localtime')
                """)) {
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
