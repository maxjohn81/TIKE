package com.mycompany.tike.ui;

import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.AuthenticationService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public final class LoginWindow extends JFrame {
    private final AuthenticationService authenticationService;
    private final Consumer<UserSession> onAuthenticated;
    private final boolean firstRun;
    private final JTextField nameField = new JTextField();
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmationField = new JPasswordField();
    private final JTextArea feedback = new JTextArea(2, 30);

    public LoginWindow(AuthenticationService authenticationService, boolean firstRun,
            Consumer<UserSession> onAuthenticated) {
        super("TIKE | " + (firstRun ? "Configuration initiale" : "Connexion"));
        this.authenticationService = authenticationService;
        this.firstRun = firstRun;
        this.onAuthenticated = onAuthenticated;
        buildWindow();
    }

    private void buildWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(480, firstRun ? 700 : 580));
        setSize(540, firstRun ? 710 : 600);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.setBorder(new EmptyBorder(28, 36, 30, 36));

        JPanel card = new JPanel(new BorderLayout(0, 20));
        card.setBackground(UiTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(28, 30, 28, 30)));
        card.add(buildHeader(), BorderLayout.NORTH);
        card.add(buildForm(), BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 8));
        header.setOpaque(false);
        java.net.URL logoUrl = LoginWindow.class.getResource("/images/logo.png");
        if (logoUrl != null) {
            ImageIcon source = new ImageIcon(logoUrl);
            java.awt.Image scaled = source.getImage().getScaledInstance(68, 68, java.awt.Image.SCALE_SMOOTH);
            JLabel logo = new JLabel(new ImageIcon(scaled), SwingConstants.CENTER);
            header.add(logo);
        }
        JLabel title = new JLabel(firstRun ? "Créer l'administrateur" : "Connexion", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 25));
        title.setForeground(UiTheme.TEXT);
        JLabel subtitle = new JLabel(
                firstRun ? "Première configuration sécurisée de TIKE" : "Connectez-vous à votre espace TIKE",
                SwingConstants.CENTER);
        subtitle.setForeground(UiTheme.MUTED);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.setOpaque(false);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 13));
        fields.setOpaque(false);
        if (firstRun) {
            fields.add(UiComponents.formRow("Nom complet", nameField));
        }
        fields.add(UiComponents.formRow("Nom d'utilisateur", usernameField));
        fields.add(UiComponents.formRow("Mot de passe", passwordField));
        if (firstRun) {
            fields.add(UiComponents.formRow("Confirmer le mot de passe", confirmationField));
        }

        JButton submit = UiComponents.primaryButton(firstRun ? "Créer le compte administrateur" : "Se connecter");
        submit.addActionListener(event -> submit());
        feedback.setForeground(UiTheme.ERROR);
        feedback.setFont(UiTheme.FONT);
        feedback.setLineWrap(true);
        feedback.setWrapStyleWord(true);
        feedback.setEditable(false);
        feedback.setFocusable(false);
        feedback.setOpaque(false);
        feedback.setBorder(new EmptyBorder(8, 2, 8, 2));
        feedback.setPreferredSize(new Dimension(350, 56));
        feedback.setMinimumSize(new Dimension(250, 56));
        submit.setAlignmentX(LEFT_ALIGNMENT);
        fields.setAlignmentX(LEFT_ALIGNMENT);
        feedback.setAlignmentX(LEFT_ALIGNMENT);
        form.add(fields);
        form.add(javax.swing.Box.createVerticalStrut(18));
        form.add(submit);
        form.add(feedback);
        getRootPane().setDefaultButton(submit);
        return form;
    }

    private void submit() {
        feedback.setText("");
        char[] password = passwordField.getPassword();
        char[] confirmation = firstRun ? confirmationField.getPassword() : new char[0];
        try {
            UserSession session;
            if (firstRun) {
                session = authenticationService.createInitialAdmin(
                        nameField.getText(), usernameField.getText(), password, confirmation);
                JOptionPane.showMessageDialog(
                        this,
                        "Le compte administrateur a bien été créé.\n"
                                + "Identifiant interne généré automatiquement : " + session.getUserId() + "\n"
                                + "Nom d'utilisateur pour se connecter : " + session.getUsername() + "\n"
                                + "Vous allez accéder à votre espace TIKE.",
                        "Compte créé",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                if (usernameField.getText().isBlank()) {
                    feedback.setText("Le champ « Nom d'utilisateur » est vide.");
                    usernameField.requestFocusInWindow();
                    return;
                }
                if (password.length == 0 || new String(password).isBlank()) {
                    feedback.setText("Le champ « Mot de passe » est vide.");
                    passwordField.requestFocusInWindow();
                    return;
                }
                session = authenticationService.authenticate(usernameField.getText(), password);
                if (session == null) {
                    feedback.setText("Identifiant ou mot de passe incorrect.");
                    return;
                }
            }
            dispose();
            onAuthenticated.accept(session);
        } catch (IllegalArgumentException | SecurityException e) {
            feedback.setText(e.getMessage());
        } catch (SQLException | IllegalStateException e) {
            String message = describeDatabaseError(e);
            feedback.setText(message);
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    firstRun ? "Création du compte impossible" : "Connexion impossible",
                    JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "L'opération a échoué. Vérifiez les champs et réessayez."
                    : e.getMessage();
            feedback.setText(message);
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    firstRun ? "Création du compte impossible" : "Connexion impossible",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            passwordField.setText("");
            confirmationField.setText("");
        }
    }

    private String describeDatabaseError(Exception error) {
        if (error instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
            return "Ce nom d'utilisateur est déjà utilisé. Choisissez-en un autre.";
        }
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) {
            return firstRun
                    ? "Le compte administrateur n'a pas pu être créé. Vérifiez la base de données et réessayez."
                    : "La connexion a échoué. Vérifiez l'identifiant, le mot de passe et la base de données.";
        }
        return (firstRun ? "Création impossible : " : "Connexion impossible : ") + detail;
    }
}
