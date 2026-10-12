package com.mycompany.tike.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/tike";
    private static final Properties LOCAL_PROPERTIES = loadLocalProperties();

    private DatabaseConfig() {
    }

    public static String getUrl() {
        return getSetting("DB_URL", DEFAULT_URL);
    }

    public static String getUser() {
        return getSetting("DB_USER", null);
    }

    private static String getPassword() {
        return getSetting("DB_PASSWORD", null);
    }

    public static Connection getConnection() throws SQLException {
        String user = getUser();
        String password = getPassword();
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Configurez DB_USER et DB_PASSWORD dans db.properties ou dans les variables d'environnement.");
        }
        return DriverManager.getConnection(getUrl(), user, password);
    }

    private static String getSetting(String key, String defaultValue) {
        String environmentValue = System.getenv(key);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        String localValue = LOCAL_PROPERTIES.getProperty(key);
        if (localValue != null && !localValue.isBlank()) {
            return localValue;
        }
        return defaultValue;
    }

    private static Properties loadLocalProperties() {
        Properties properties = new Properties();
        Path propertiesFile = Path.of("db.properties");
        if (Files.exists(propertiesFile)) {
            try (InputStream input = Files.newInputStream(propertiesFile)) {
                properties.load(input);
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de lire le fichier local db.properties.", e);
            }
        }
        return properties;
    }
}
