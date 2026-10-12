package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class CatalogDao {
    public List<Venue> findVenues(String search) throws SQLException {
        String sql = """
                SELECT id, nom, adresse, ville, description
                FROM lieux
                WHERE LOWER(nom) LIKE LOWER(?) OR LOWER(COALESCE(ville, '')) LIKE LOWER(?)
                ORDER BY nom
                """;
        String pattern = "%" + (search == null ? "" : search.trim()) + "%";
        List<Venue> venues = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    venues.add(new Venue(
                            result.getLong("id"),
                            result.getString("nom"),
                            result.getString("adresse"),
                            result.getString("ville"),
                            result.getString("description")));
                }
            }
        }
        return venues;
    }

    public Venue saveVenue(Venue venue) throws SQLException {
        String sql = venue.id() == 0
                ? "INSERT INTO lieux (nom, adresse, ville, description) VALUES (?, ?, ?, ?) RETURNING id"
                : "UPDATE lieux SET nom = ?, adresse = ?, ville = ?, description = ? WHERE id = ? RETURNING id";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindVenue(statement, venue);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Ce lieu n'existe plus.");
                }
                return new Venue(result.getLong(1), venue.name(), venue.address(), venue.city(), venue.description());
            }
        }
    }

    public void deleteVenue(long venueId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM lieux WHERE id = ?")) {
            statement.setLong(1, venueId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Ce lieu n'existe plus.");
            }
        }
    }

    public List<Room> findRooms(String search) throws SQLException {
        String sql = """
                SELECT s.id, s.lieu_id, l.nom AS lieu_nom, s.nom AS salle_nom, s.capacite
                FROM salles s
                JOIN lieux l ON l.id = s.lieu_id
                WHERE LOWER(s.nom) LIKE LOWER(?) OR LOWER(l.nom) LIKE LOWER(?)
                ORDER BY l.nom, s.nom
                """;
        String pattern = "%" + (search == null ? "" : search.trim()) + "%";
        List<Room> rooms = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rooms.add(mapRoom(result));
                }
            }
        }
        return rooms;
    }

    public Room saveRoom(Room room) throws SQLException {
        String sql = room.id() == 0
                ? """
                  INSERT INTO salles (lieu_id, nom, capacite) VALUES (?, ?, ?)
                  RETURNING id, lieu_id, nom, capacite
                  """
                : """
                  UPDATE salles SET lieu_id = ?, nom = ?, capacite = ? WHERE id = ?
                  RETURNING id, lieu_id, nom, capacite
                  """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                if (room.id() != 0) {
                    try (PreparedStatement lock = connection.prepareStatement(
                            "SELECT id FROM salles WHERE id = ? FOR UPDATE")) {
                        lock.setLong(1, room.id());
                        try (ResultSet result = lock.executeQuery()) {
                            if (!result.next()) {
                                throw new IllegalArgumentException("Cette salle n'existe plus.");
                            }
                        }
                    }
                    try (PreparedStatement query = connection.prepareStatement("""
                            SELECT e.nom
                            FROM evenements e
                            JOIN categories_billets c ON c.evenement_id = e.id
                            WHERE e.salle_id = ?
                            GROUP BY e.id, e.nom
                            HAVING SUM(c.quantite_totale) > ?
                            LIMIT 1
                            """)) {
                        query.setLong(1, room.id());
                        query.setInt(2, room.capacity());
                        try (ResultSet result = query.executeQuery()) {
                            if (result.next()) {
                                throw new IllegalArgumentException(
                                        "La capacité ne peut pas être réduite : les catégories de l'événement « "
                                                + result.getString(1) + " » dépassent cette valeur.");
                            }
                        }
                    }
                }
                statement.setLong(1, room.venueId());
                statement.setString(2, room.name());
                statement.setInt(3, room.capacity());
                if (room.id() != 0) {
                    statement.setLong(4, room.id());
                }
                long roomId;
                long venueId;
                String roomName;
                int capacity;
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Cette salle n'existe plus.");
                    }
                    roomId = result.getLong("id");
                    venueId = result.getLong("lieu_id");
                    roomName = result.getString("nom");
                    capacity = result.getInt("capacite");
                }
                String venueName;
                try (PreparedStatement venueQuery = connection.prepareStatement("SELECT nom FROM lieux WHERE id = ?")) {
                    venueQuery.setLong(1, venueId);
                    try (ResultSet venue = venueQuery.executeQuery()) {
                        if (!venue.next()) {
                            throw new SQLException("Le lieu de la salle n'existe plus.");
                        }
                        venueName = venue.getString(1);
                    }
                }
                connection.commit();
                return new Room(roomId, venueId, venueName, roomName, capacity);
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public void deleteRoom(long roomId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM salles WHERE id = ?")) {
            statement.setLong(1, roomId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Cette salle n'existe plus.");
            }
        }
    }

    public List<Event> findEvents(String search, EventStatus status) throws SQLException {
        String sql = """
                SELECT e.id, e.nom, e.description, e.type, e.salle_id,
                       s.nom AS salle_nom, l.nom AS lieu_nom, e.debut, e.fin,
                       e.ouverture_vente, e.fermeture_vente, e.statut
                FROM evenements e
                LEFT JOIN salles s ON s.id = e.salle_id
                LEFT JOIN lieux l ON l.id = s.lieu_id
                WHERE (LOWER(e.nom) LIKE LOWER(?) OR LOWER(COALESCE(e.type, '')) LIKE LOWER(?)
                       OR LOWER(COALESCE(l.nom, '')) LIKE LOWER(?)
                       OR LOWER(COALESCE(s.nom, '')) LIKE LOWER(?))
                  AND (? IS NULL OR e.statut = ?)
                ORDER BY e.debut DESC, e.nom
                """;
        String pattern = "%" + (search == null ? "" : search.trim()) + "%";
        List<Event> events = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, status == null ? null : status.name());
            statement.setString(6, status == null ? null : status.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    events.add(mapEvent(result));
                }
            }
        }
        return events;
    }

    public Event saveEvent(Event event) throws SQLException {
        String sql = event.id() == 0
                ? """
                  INSERT INTO evenements
                    (nom, description, type, salle_id, debut, fin, ouverture_vente, fermeture_vente, statut)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                  RETURNING id
                  """
                : """
                  UPDATE evenements
                  SET nom = ?, description = ?, type = ?, salle_id = ?, debut = ?, fin = ?,
                      ouverture_vente = ?, fermeture_vente = ?, statut = ?
                  WHERE id = ?
                  RETURNING id
                  """;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                if (event.id() != 0) {
                    lockEvent(connection, event.id());
                }
                Integer roomCapacity = event.roomId() == null ? null : lockRoom(connection, event.roomId());
                ensureNoRoomConflict(connection, event);
                ensureCategoriesFitRoom(connection, event.id(), roomCapacity);
                long savedId;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindEvent(statement, event);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Cet événement n'existe plus.");
                        }
                        savedId = result.getLong(1);
                    }
                }
                connection.commit();
                return new Event(
                        savedId, event.name(), event.description(), event.type(), event.roomId(), event.roomName(),
                        event.startsAt(), event.endsAt(), event.salesOpenAt(), event.salesCloseAt(), event.status());
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public List<TicketCategory> findCategories(long eventId) throws SQLException {
        String sql = """
                SELECT c.id, c.evenement_id, c.nom, c.prix, c.quantite_totale,
                       s.quantite_disponible, c.description
                FROM categories_billets c
                JOIN stock_categories s ON s.categorie_id = c.id
                WHERE c.evenement_id = ?
                ORDER BY c.prix, c.nom
                """;
        List<TicketCategory> categories = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    categories.add(mapCategory(result));
                }
            }
        }
        return categories;
    }

    public TicketCategory saveCategory(TicketCategory category) throws SQLException {
        String sql = category.id() == 0
                ? """
                  INSERT INTO categories_billets
                    (evenement_id, nom, prix, quantite_totale, description)
                  VALUES (?, ?, ?, ?, ?)
                  RETURNING id
                  """
                : """
                  UPDATE categories_billets
                  SET nom = ?, prix = ?, quantite_totale = ?, description = ?
                  WHERE id = ? AND evenement_id = ?
                  RETURNING id
                  """;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                Integer roomCapacity = lockEvent(connection, category.eventId());
                long activeTicketCount = countActiveTickets(connection, category.id());
                if (category.totalQuantity() < activeTicketCount) {
                    throw new IllegalArgumentException(
                            "La quantité totale ne peut pas être inférieure aux billets déjà émis ("
                                    + activeTicketCount + ").");
                }
                ensureCapacity(connection, category.eventId(), category.id(), category.totalQuantity(), roomCapacity);
                long savedId;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (category.id() == 0) {
                        statement.setLong(1, category.eventId());
                        statement.setString(2, category.name());
                        statement.setBigDecimal(3, category.price());
                        statement.setInt(4, category.totalQuantity());
                        statement.setString(5, category.description());
                    } else {
                        statement.setString(1, category.name());
                        statement.setBigDecimal(2, category.price());
                        statement.setInt(3, category.totalQuantity());
                        statement.setString(4, category.description());
                        statement.setLong(5, category.id());
                        statement.setLong(6, category.eventId());
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Cette catégorie n'existe plus.");
                        }
                        savedId = result.getLong(1);
                    }
                }
                connection.commit();
                return new TicketCategory(
                        savedId, category.eventId(), category.name(), category.price(),
                        category.totalQuantity(), category.totalQuantity(), category.description());
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public void deleteCategory(long eventId, long categoryId) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockEvent(connection, eventId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM categories_billets WHERE id = ? AND evenement_id = ?")) {
                    statement.setLong(1, categoryId);
                    statement.setLong(2, eventId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("La catégorie n'existe plus.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    private static void ensureNoRoomConflict(Connection connection, Event event) throws SQLException {
        if (event.roomId() == null) {
            return;
        }
        String sql = """
                SELECT nom
                FROM evenements
                WHERE salle_id = ?
                  AND statut NOT IN ('ANNULE', 'TERMINE')
                  AND (? = 0 OR id <> ?)
                  AND debut < COALESCE(?, ? + INTERVAL '1 hour')
                  AND COALESCE(fin, debut + INTERVAL '1 hour') > ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, event.roomId());
            statement.setLong(2, event.id());
            statement.setLong(3, event.id());
            setDateTime(statement, 4, event.endsAt());
            setDateTime(statement, 5, event.startsAt());
            setDateTime(statement, 6, event.startsAt());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException(
                            "La salle est déjà réservée pour un événement qui chevauche « "
                                    + result.getString(1) + " ».");
                }
            }
        }
    }

    private static void ensureCategoriesFitRoom(Connection connection, long eventId, Integer roomCapacity)
            throws SQLException {
        if (eventId == 0 || roomCapacity == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(quantite_totale), 0) FROM categories_billets WHERE evenement_id = ?")) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) > roomCapacity) {
                    throw new IllegalArgumentException(
                            "La capacité totale des catégories dépasse la capacité de la salle.");
                }
            }
        }
    }

    private static void ensureCapacity(
            Connection connection, long eventId, long categoryId, int newQuantity, Integer roomCapacity)
            throws SQLException {
        if (roomCapacity == null) {
            return;
        }
        String sql = """
                SELECT COALESCE(SUM(quantite_totale), 0)
                FROM categories_billets
                WHERE evenement_id = ? AND (? = 0 OR id <> ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setLong(2, categoryId);
            statement.setLong(3, categoryId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                long reservedCapacity = result.getLong(1) + newQuantity;
                if (reservedCapacity > roomCapacity) {
                    throw new IllegalArgumentException(
                            "La capacité configurée (" + reservedCapacity
                                    + ") dépasse celle de la salle (" + roomCapacity + ").");
                }
            }
        }
    }

    private static Integer lockRoom(Connection connection, long roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT capacite FROM salles WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, roomId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("La salle sélectionnée n'existe plus.");
                }
                return result.getInt(1);
            }
        }
    }

    private static Integer lockEvent(Connection connection, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.capacite
                FROM evenements e
                LEFT JOIN salles s ON s.id = e.salle_id
                WHERE e.id = ?
                FOR UPDATE OF e
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("L'événement sélectionné n'existe plus.");
                }
                int capacity = result.getInt(1);
                return result.wasNull() ? null : capacity;
            }
        }
    }

    private static long countActiveTickets(Connection connection, long categoryId) throws SQLException {
        if (categoryId == 0) {
            return 0;
        }
        String sql = """
                SELECT COUNT(*)
                FROM billets
                WHERE categorie_id = ? AND statut NOT IN ('ANNULE', 'REMBOURSE')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, categoryId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void bindVenue(PreparedStatement statement, Venue venue) throws SQLException {
        statement.setString(1, venue.name());
        statement.setString(2, venue.address());
        statement.setString(3, venue.city());
        statement.setString(4, venue.description());
        if (venue.id() != 0) {
            statement.setLong(5, venue.id());
        }
    }

    private static void bindEvent(PreparedStatement statement, Event event) throws SQLException {
        statement.setString(1, event.name());
        statement.setString(2, event.description());
        statement.setString(3, event.type());
        if (event.roomId() == null) {
            statement.setNull(4, java.sql.Types.BIGINT);
        } else {
            statement.setLong(4, event.roomId());
        }
        setDateTime(statement, 5, event.startsAt());
        setDateTime(statement, 6, event.endsAt());
        setDateTime(statement, 7, event.salesOpenAt());
        setDateTime(statement, 8, event.salesCloseAt());
        statement.setString(9, event.status().name());
        if (event.id() != 0) {
            statement.setLong(10, event.id());
        }
    }

    private static void setDateTime(PreparedStatement statement, int index, LocalDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(index, value.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
        }
    }

    private static LocalDateTime getDateTime(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Room mapRoom(ResultSet result) throws SQLException {
        return new Room(
                result.getLong("id"),
                result.getLong("lieu_id"),
                result.getString("lieu_nom"),
                result.getString("salle_nom"),
                result.getInt("capacite"));
    }

    private static Event mapEvent(ResultSet result) throws SQLException {
        long roomIdValue = result.getLong("salle_id");
        Long roomId = result.wasNull() ? null : roomIdValue;
        String roomName = result.getString("lieu_nom");
        String room = result.getString("salle_nom");
        if (roomName != null && room != null) {
            roomName += " — " + room;
        }
        return new Event(
                result.getLong("id"),
                result.getString("nom"),
                result.getString("description"),
                result.getString("type"),
                roomId,
                roomName,
                getDateTime(result, "debut"),
                getDateTime(result, "fin"),
                getDateTime(result, "ouverture_vente"),
                getDateTime(result, "fermeture_vente"),
                EventStatus.valueOf(result.getString("statut")));
    }

    private static TicketCategory mapCategory(ResultSet result) throws SQLException {
        return new TicketCategory(
                result.getLong("id"),
                result.getLong("evenement_id"),
                result.getString("nom"),
                result.getBigDecimal("prix"),
                result.getInt("quantite_totale"),
                result.getInt("quantite_disponible"),
                result.getString("description"));
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
