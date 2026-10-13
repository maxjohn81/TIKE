package com.mycompany.tike.dao;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserCredential;
import com.mycompany.tike.model.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserDao {
    public Optional<UserCredential> findActiveByUsername(String username) throws SQLException {
        String sql = """
                SELECT id, nom_complet, nom_utilisateur, role, actif, mot_de_passe_hash
                FROM utilisateurs
                WHERE nom_utilisateur = ? AND actif = TRUE
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserCredential(mapUser(result), result.getString("mot_de_passe_hash")));
            }
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = """
                SELECT id, nom_complet, nom_utilisateur, role, actif
                FROM utilisateurs
                ORDER BY nom_complet, nom_utilisateur
                """;
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(mapUser(result));
            }
        }
        return users;
    }

    public long countUsers() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM utilisateurs");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    public User createInitialAdmin(String fullName, String username, String passwordHash) throws SQLException {
        String sql = """
                INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
                VALUES (?, ?, ?, 'ADMIN', TRUE)
                RETURNING id, nom_complet, nom_utilisateur, role, actif
                """;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (countUsers(connection) != 0) {
                    throw new IllegalStateException("Un compte existe déjà. Connectez-vous avec un compte administrateur.");
                }
                User user;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, fullName);
                    statement.setString(2, username);
                    statement.setString(3, passwordHash);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        user = mapUser(result);
                    }
                }
                connection.commit();
                return user;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public User create(String fullName, String username, String passwordHash, UserRole role) throws SQLException {
        String sql = """
                INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
                VALUES (?, ?, ?, ?, TRUE)
                RETURNING id, nom_complet, nom_utilisateur, role, actif
                """;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                User user;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, fullName);
                    statement.setString(2, username);
                    statement.setString(3, passwordHash);
                    statement.setString(4, role.name());
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        user = mapUser(result);
                    }
                }
                connection.commit();
                return user;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public User update(User user, String passwordHash) throws SQLException {
        String updateUser = """
                UPDATE utilisateurs
                SET nom_complet = ?, nom_utilisateur = ?, role = ?, actif = ?
                WHERE id = ?
                """;
        String updatePassword = "UPDATE utilisateurs SET mot_de_passe_hash = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                if (user.getRole() != UserRole.ADMIN || !user.isActive()) {
                    ensureAnotherActiveAdmin(connection, user.getId());
                }
                try (PreparedStatement statement = connection.prepareStatement(updateUser)) {
                    statement.setString(1, user.getFullName());
                    statement.setString(2, user.getUsername());
                    statement.setString(3, user.getRole().name());
                    statement.setBoolean(4, user.isActive());
                    statement.setLong(5, user.getId());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Cet utilisateur n'existe plus.");
                    }
                }
                if (passwordHash != null) {
                    try (PreparedStatement statement = connection.prepareStatement(updatePassword)) {
                        statement.setString(1, passwordHash);
                        statement.setLong(2, user.getId());
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalArgumentException("Cet utilisateur n'existe plus.");
                        }
                    }
                }
                connection.commit();
                return user;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public User deactivate(long userId) throws SQLException {
        String sql = """
                UPDATE utilisateurs
                SET actif = FALSE
                WHERE id = ?
                RETURNING id, nom_complet, nom_utilisateur, role, actif
                """;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                User user;
                try (PreparedStatement find = connection.prepareStatement(
                        "SELECT role FROM utilisateurs WHERE id = ?")) {
                    find.setLong(1, userId);
                    try (ResultSet result = find.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Cet utilisateur n'existe plus.");
                        }
                        if (UserRole.ADMIN.name().equals(result.getString(1))) {
                            ensureAnotherActiveAdmin(connection, userId);
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        user = mapUser(result);
                    }
                }
                connection.commit();
                return user;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    private static long countUsers(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM utilisateurs");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void ensureAnotherActiveAdmin(Connection connection, long excludedUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM utilisateurs
                WHERE role = 'ADMIN' AND actif = TRUE AND id <> ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, excludedUserId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) == 0) {
                    throw new IllegalArgumentException("Il doit rester au moins un administrateur actif.");
                }
            }
        }
    }

    private static User mapUser(ResultSet result) throws SQLException {
        return new User(
                result.getLong("id"),
                result.getString("nom_complet"),
                result.getString("nom_utilisateur"),
                UserRole.valueOf(result.getString("role")),
                result.getBoolean("actif"));
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
