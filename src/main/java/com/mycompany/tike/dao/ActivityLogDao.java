package com.mycompany.tike.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class ActivityLogDao {
    private ActivityLogDao() {
    }

    static void record(
            Connection connection, long actorId, String action, String objectType, long objectId, String details)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO journal_activites (utilisateur_id, action, type_objet, objet_id, details)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, actorId);
            statement.setString(2, action);
            statement.setString(3, objectType);
            statement.setLong(4, objectId);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }
}
