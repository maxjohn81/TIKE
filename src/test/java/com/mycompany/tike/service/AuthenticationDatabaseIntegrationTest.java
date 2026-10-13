package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.UserDao;
import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AuthenticationDatabaseIntegrationTest {

    @Test
    void databaseAuthenticationUserManagementAndRoleRestrictionsWork() throws SQLException {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec SQLite.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String adminUsername = "it.admin." + suffix;
        String agentUsername = "it.agent." + suffix;
        String updatedAgentUsername = "it.agent.updated." + suffix;
        String controllerUsername = "it.controller." + suffix;
        String adminPassword = "TikeAdmin-" + suffix + "-123";
        String agentPassword = "TikeAgent-" + suffix + "-123";
        String updatedAgentPassword = "TikeUpdated-" + suffix + "-123";
        String controllerPassword = "TikeController-" + suffix + "-123";
        UserDao userDao = new UserDao();
        AuthenticationService service = new AuthenticationService(userDao);

        try {
            User admin;
            UserSession adminSession;
            if (userDao.countUsers() == 0) {
                adminSession = service.createInitialAdmin(
                        "TIKE Integration Admin", adminUsername,
                        adminPassword.toCharArray(), adminPassword.toCharArray());
                admin = new User(
                        adminSession.getUserId(),
                        adminSession.getDisplayName(),
                        adminSession.getUsername(),
                        adminSession.getRole(),
                        true);
            } else {
                admin = userDao.create(
                        "TIKE Integration Admin", adminUsername,
                        com.mycompany.tike.util.PasswordUtil.hash(adminPassword), UserRole.ADMIN);
                adminSession = service.authenticate(adminUsername, adminPassword.toCharArray());
            }
            assertNotNull(adminSession);
            assertEquals(UserRole.ADMIN, adminSession.getRole());
            assertNull(service.authenticate(adminUsername, "incorrect-password".toCharArray()));

            User agent = service.createUser(
                    adminSession, "TIKE Integration Agent", agentUsername, UserRole.AGENT,
                    agentPassword.toCharArray(), agentPassword.toCharArray());
            UserSession agentSession = service.authenticate(agentUsername, agentPassword.toCharArray());
            assertNotNull(agentSession);
            assertEquals(UserRole.AGENT, agentSession.getRole());
            assertThrows(SecurityException.class, () -> service.listUsers(agentSession));

            service.createUser(
                    adminSession, "TIKE Integration Controller", controllerUsername, UserRole.CONTROLEUR,
                    controllerPassword.toCharArray(), controllerPassword.toCharArray());
            UserSession controllerSession = service.authenticate(controllerUsername, controllerPassword.toCharArray());
            assertNotNull(controllerSession);
            assertEquals(UserRole.CONTROLEUR, controllerSession.getRole());
            assertThrows(SecurityException.class, () -> service.listUsers(controllerSession));

            User updatedAgent = new User(
                    agent.getId(), agent.getFullName(), updatedAgentUsername, UserRole.AGENT, true);
            service.updateUser(
                    adminSession, updatedAgent,
                    updatedAgentPassword.toCharArray(), updatedAgentPassword.toCharArray());
            assertNull(service.authenticate(agentUsername, agentPassword.toCharArray()));
            assertNotNull(service.authenticate(updatedAgentUsername, updatedAgentPassword.toCharArray()));

            service.deactivateUser(adminSession, agent.getId());
            assertNull(service.authenticate(updatedAgentUsername, updatedAgentPassword.toCharArray()));
            assertThrows(IllegalArgumentException.class, () -> service.deactivateUser(adminSession, admin.getId()));
        } finally {
            deleteTestUsers(adminUsername, agentUsername, updatedAgentUsername, controllerUsername);
        }
    }

    private static void deleteTestUsers(
            String adminUsername, String agentUsername, String updatedAgentUsername, String controllerUsername)
            throws SQLException {
        String sql = "DELETE FROM utilisateurs WHERE nom_utilisateur IN (?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, adminUsername);
            statement.setString(2, agentUsername);
            statement.setString(3, updatedAgentUsername);
            statement.setString(4, controllerUsername);
            statement.executeUpdate();
        }
    }
}
