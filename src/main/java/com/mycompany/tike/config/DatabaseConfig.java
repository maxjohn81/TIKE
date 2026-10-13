package com.mycompany.tike.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.sqlite.SQLiteConfig;

public final class DatabaseConfig {
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String SQLITE_SCHEMA = "/schema-sqlite.sql";
    private static boolean initialized;

    private DatabaseConfig() {
    }

    public static Path getDatabasePath() {
        String configuredPath = System.getenv("TIKE_DB_PATH");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        Path dataDirectory = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(localAppData);
        return dataDirectory.resolve("TIKE").resolve("tike.db");
    }

    public static String getUrl() {
        return "jdbc:sqlite:" + getDatabasePath();
    }

    public static Connection getConnection() throws SQLException {
        synchronized (DatabaseConfig.class) {
            Path databasePath = getDatabasePath();
            try {
                Files.createDirectories(databasePath.getParent());
            } catch (IOException e) {
                throw new SQLException("Impossible de créer le dossier de la base TIKE.", e);
            }
            Connection connection = openSqliteConnection(databasePath);
            if (!initialized) {
                try {
                    initializeSchema(connection);
                    initialized = true;
                } catch (SQLException e) {
                    connection.close();
                    throw e;
                }
            }
            return connection;
        }
    }

    public static Connection openSqliteConnection(Path path) throws SQLException {
        try {
            Class.forName(SQLITE_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Le pilote SQLite n'est pas disponible.", e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + path.toAbsolutePath().normalize(), config.toProperties());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    public static void initializeSchema(Connection connection) throws SQLException {
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(SQLITE_SCHEMA)) {
            if (input == null) {
                throw new SQLException("Le schéma SQLite est absent du paquet TIKE.");
            }
            String schema = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String sql : schema.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
        } catch (IOException e) {
            throw new SQLException("Impossible de lire le schéma SQLite.", e);
        }
    }

}
