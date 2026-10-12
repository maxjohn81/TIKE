package com.mycompany.tike.app;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.UserDao;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.AuthenticationService;
import com.mycompany.tike.ui.LoginWindow;
import com.mycompany.tike.ui.MainWindow;
import com.mycompany.tike.ui.UiTheme;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class TikeApplication {

    private TikeApplication() {
    }

    public static void main(String[] args) {
        UserDao userDao = new UserDao();
        AuthenticationService authenticationService = new AuthenticationService(userDao);
        boolean firstRun;
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (connection.isClosed()) {
                throw new SQLException("La connexion PostgreSQL a été fermée immédiatement après son ouverture.");
            }
            firstRun = userDao.countUsers() == 0;
        } catch (IllegalStateException | SQLException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    "Connexion PostgreSQL impossible : " + e.getMessage(),
                    "Erreur de connexion",
                    JOptionPane.ERROR_MESSAGE));
            return;
        }
        UiTheme.apply();
        showLogin(authenticationService, firstRun);
    }

    private static void showLogin(AuthenticationService authenticationService, boolean firstRun) {
        SwingUtilities.invokeLater(() -> new LoginWindow(
                authenticationService,
                firstRun,
                session -> showMainWindow(authenticationService, session)).setVisible(true));
    }

    private static void showMainWindow(AuthenticationService authenticationService, UserSession session) {
        SwingUtilities.invokeLater(() -> new MainWindow(
                session,
                authenticationService,
                () -> showLogin(authenticationService, false)).setVisible(true));
    }
}
