package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ReportingDao {
    public DashboardMetrics dashboardMetrics() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         (SELECT COUNT(*) FROM evenements
                          WHERE statut IN ('PROGRAMME', 'EN_VENTE', 'COMPLET')) AS active_events,
                         (SELECT COUNT(*) FROM billets b
                          JOIN ventes v ON v.id = b.vente_id
                          WHERE v.statut = 'PAYEE' AND b.statut IN ('VALIDE', 'UTILISE')) AS sold_tickets,
                         (SELECT COALESCE(SUM(montant_total), 0) FROM ventes
                          WHERE statut = 'PAYEE') AS revenue,
                         (SELECT COUNT(*) FROM reservations
                          WHERE statut = 'EN_ATTENTE' AND expire_le > datetime('now', 'localtime')) AS pending_reservations
                     """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new DashboardMetrics(
                    result.getLong("active_events"),
                    result.getLong("sold_tickets"),
                    result.getBigDecimal("revenue"),
                    result.getLong("pending_reservations"));
        }
    }

    public List<ChartValue> monthlyRevenue() throws SQLException {
        List<ChartValue> values = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     WITH RECURSIVE months(month_start, month_index) AS (
                         SELECT date('now', 'localtime', 'start of month', '-5 months'), 0
                         UNION ALL
                         SELECT date(month_start, '+1 month'), month_index + 1
                         FROM months
                         WHERE month_index < 5
                     )
                     SELECT strftime('%Y-%m-%d %H:%M:%f', months.month_start) AS month_start,
                            COALESCE(SUM(v.montant_total), 0) AS revenue
                     FROM months
                     LEFT JOIN ventes v
                       ON v.date_vente >= months.month_start
                      AND v.date_vente < date(months.month_start, '+1 month')
                      AND v.statut = 'PAYEE'
                     GROUP BY months.month_start
                     ORDER BY months.month_start
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Timestamp month = result.getTimestamp("month_start");
                values.add(new ChartValue(
                        month.toLocalDateTime().toLocalDate()
                                .format(java.time.format.DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.FRENCH)),
                        result.getBigDecimal("revenue")));
            }
        }
        return values;
    }

    public List<ChartValue> ticketDistributionByEvent() throws SQLException {
        List<ChartValue> values = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     WITH event_sales AS (
                         SELECT e.nom AS event_name, COUNT(b.id) AS ticket_count
                         FROM evenements e
                         JOIN billets b ON b.evenement_id = e.id
                         JOIN ventes v ON v.id = b.vente_id
                         WHERE v.statut = 'PAYEE' AND b.statut IN ('VALIDE', 'UTILISE')
                         GROUP BY e.id, e.nom
                     ),
                     ranked AS (
                         SELECT event_name, ticket_count,
                                ROW_NUMBER() OVER (ORDER BY ticket_count DESC, event_name) AS rank
                         FROM event_sales
                     )
                     SELECT event_name, ticket_count
                     FROM ranked
                     WHERE rank <= 5
                     UNION ALL
                     SELECT 'Autres événements', SUM(ticket_count)
                     FROM ranked
                     HAVING COUNT(*) > 5
                     ORDER BY ticket_count DESC
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(new ChartValue(result.getString("event_name"),
                        BigDecimal.valueOf(result.getLong("ticket_count"))));
            }
        }
        return values;
    }

    public List<EventOption> findEventOptions() throws SQLException {
        List<EventOption> events = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, nom FROM evenements ORDER BY debut DESC, nom");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                events.add(new EventOption(result.getLong("id"), result.getString("nom")));
            }
        }
        return events;
    }

    public List<SalesReportRow> findSales(LocalDate from, LocalDate through, Long eventId) throws SQLException {
        List<SalesReportRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT v.numero_vente, e.nom AS evenement, v.date_vente,
                            COALESCE(c.nom_complet, 'Client non renseigné') AS client,
                            v.montant_total, v.statut,
                            (SELECT COUNT(*) FROM billets b WHERE b.vente_id = v.id
                             AND b.statut IN ('VALIDE', 'UTILISE')) AS billets
                     FROM ventes v
                     JOIN evenements e ON e.id = v.evenement_id
                     LEFT JOIN clients c ON c.id = v.client_id
                     WHERE (? IS NULL OR v.date_vente >= ?)
                       AND (? IS NULL OR v.date_vente < ?)
                       AND (? IS NULL OR v.evenement_id = ?)
                     ORDER BY v.date_vente DESC, v.id DESC
                     """)) {
            bindDateRange(statement, from, through);
            bindOptionalId(statement, 5, 6, eventId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp soldAt = result.getTimestamp("date_vente");
                    rows.add(new SalesReportRow(
                            result.getString("numero_vente"),
                            result.getString("evenement"),
                            soldAt.toLocalDateTime(),
                            result.getString("client"),
                            result.getBigDecimal("montant_total"),
                            result.getString("statut"),
                            result.getLong("billets")));
                }
            }
        }
        return rows;
    }

    public List<EventFillRow> findEventFill(LocalDate from, LocalDate through, Long eventId) throws SQLException {
        List<EventFillRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT e.id, e.nom, e.debut,
                            (SELECT COALESCE(SUM(cb.quantite_totale), 0)
                             FROM categories_billets cb WHERE cb.evenement_id = e.id) AS capacity,
                            (SELECT COUNT(*) FROM billets b
                             JOIN ventes v ON v.id = b.vente_id
                             WHERE b.evenement_id = e.id AND v.statut = 'PAYEE'
                               AND b.statut IN ('VALIDE', 'UTILISE')) AS sold
                     FROM evenements e
                     WHERE (? IS NULL OR e.debut >= ?)
                       AND (? IS NULL OR e.debut < ?)
                       AND (? IS NULL OR e.id = ?)
                     GROUP BY e.id
                     ORDER BY e.debut DESC, e.nom
                     """)) {
            bindDateRange(statement, from, through);
            bindOptionalId(statement, 5, 6, eventId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp startsAt = result.getTimestamp("debut");
                    rows.add(new EventFillRow(
                            result.getString("nom"),
                            startsAt.toLocalDateTime(),
                            result.getLong("capacity"),
                            result.getLong("sold")));
                }
            }
        }
        return rows;
    }

    public List<ActivityEntry> findActivity(LocalDate from, LocalDate through, String search) throws SQLException {
        List<ActivityEntry> entries = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT j.cree_le, COALESCE(u.nom_complet, 'Système') AS utilisateur,
                            j.action, COALESCE(j.type_objet, '') AS type_objet,
                            j.objet_id, COALESCE(j.details, '') AS details
                     FROM journal_activites j
                     LEFT JOIN utilisateurs u ON u.id = j.utilisateur_id
                     WHERE (? IS NULL OR j.cree_le >= ?)
                       AND (? IS NULL OR j.cree_le < ?)
                       AND (? = '' OR LOWER(j.action) LIKE LOWER(?) OR LOWER(COALESCE(j.details, '')) LIKE LOWER(?))
                     ORDER BY j.cree_le DESC, j.id DESC
                     LIMIT 500
                     """)) {
            bindDateRange(statement, from, through);
            String pattern = "%" + (search == null ? "" : search.trim()) + "%";
            statement.setString(5, search == null ? "" : search.trim());
            statement.setString(6, pattern);
            statement.setString(7, pattern);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp createdAt = result.getTimestamp("cree_le");
                    long objectIdValue = result.getLong("objet_id");
                    Long objectId = result.wasNull() ? null : objectIdValue;
                    entries.add(new ActivityEntry(
                            createdAt.toLocalDateTime(),
                            result.getString("utilisateur"),
                            result.getString("action"),
                            result.getString("type_objet"),
                            objectId,
                            result.getString("details")));
                }
            }
        }
        return entries;
    }

    private static void bindDateRange(PreparedStatement statement, LocalDate from, LocalDate through)
            throws SQLException {
        String start = from == null ? null : from.atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
        String endExclusive = through == null ? null : through.plusDays(1).atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
        if (start == null) {
            statement.setNull(1, java.sql.Types.TIMESTAMP);
            statement.setNull(2, java.sql.Types.TIMESTAMP);
        } else {
            statement.setString(1, start);
            statement.setString(2, start);
        }
        if (endExclusive == null) {
            statement.setNull(3, java.sql.Types.TIMESTAMP);
            statement.setNull(4, java.sql.Types.TIMESTAMP);
        } else {
            statement.setString(3, endExclusive);
            statement.setString(4, endExclusive);
        }
    }

    private static void bindOptionalId(PreparedStatement statement, int nullIndex, int valueIndex, Long id)
            throws SQLException {
        if (id == null) {
            statement.setNull(nullIndex, java.sql.Types.BIGINT);
            statement.setNull(valueIndex, java.sql.Types.BIGINT);
        } else {
            statement.setLong(nullIndex, id);
            statement.setLong(valueIndex, id);
        }
    }

    public record DashboardMetrics(long activeEvents, long soldTickets, BigDecimal revenue,
                                   long pendingReservations) {
    }

    public record ChartValue(String label, BigDecimal value) {
    }

    public record EventOption(Long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record SalesReportRow(String saleNumber, String eventName, LocalDateTime soldAt, String customer,
                                 BigDecimal amount, String status, long ticketCount) {
    }

    public record EventFillRow(String eventName, LocalDateTime startsAt, long capacity, long sold) {
        public double fillRate() {
            return capacity == 0 ? 0 : 100.0 * sold / capacity;
        }
    }

    public record ActivityEntry(LocalDateTime createdAt, String user, String action, String objectType,
                                Long objectId, String details) {
    }
}
