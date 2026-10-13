package com.mycompany.tike.ui;

import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.AuthenticationService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public final class LoginWindow extends JFrame {
    private static final String LOGO_RESOURCE = "/images/logo.png";
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
        Rectangle usableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(
                Math.min(1000, usableBounds.width),
                Math.min(firstRun ? 680 : 620, usableBounds.height)));
        setSize(
                Math.min(1280, usableBounds.width),
                Math.min(720, usableBounds.height));
        setLocation(
                usableBounds.x + (usableBounds.width - getWidth()) / 2,
                usableBounds.y + (usableBounds.height - getHeight()) / 2);

        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints brandConstraints = new GridBagConstraints();
        brandConstraints.gridx = 0;
        brandConstraints.gridy = 0;
        brandConstraints.weightx = 0.42;
        brandConstraints.weighty = 1;
        brandConstraints.fill = GridBagConstraints.BOTH;
        root.add(buildBrandPanel(), brandConstraints);
        GridBagConstraints loginConstraints = new GridBagConstraints();
        loginConstraints.gridx = 1;
        loginConstraints.gridy = 0;
        loginConstraints.weightx = 0.58;
        loginConstraints.weighty = 1;
        loginConstraints.fill = GridBagConstraints.BOTH;
        root.add(buildLoginPanel(), loginConstraints);
        setContentPane(root);
    }

    private JPanel buildBrandPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.PRIMARY_DARK);

        JPanel branding = new JPanel();
        branding.setOpaque(false);
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        branding.add(buildLogoBadge());
        branding.add(Box.createVerticalStrut(22));

        JLabel name = new JLabel("TIKE", SwingConstants.CENTER);
        name.setFont(new Font("Segoe UI", Font.BOLD, 54));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(CENTER_ALIGNMENT);
        branding.add(name);
        branding.add(Box.createVerticalStrut(10));

        JLabel tagline = new JLabel(
                "<html><div style='text-align:center'>La billetterie et le contrôle d’accès,<br>"
                        + "en toute simplicité.</div></html>",
                SwingConstants.CENTER);
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        tagline.setForeground(Color.WHITE);
        tagline.setAlignmentX(CENTER_ALIGNMENT);
        branding.add(tagline);
        branding.add(Box.createVerticalStrut(18));

        JLabel details = new JLabel(
                "Gérez vos événements, vos ventes et vos accès.",
                SwingConstants.CENTER);
        details.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        details.setForeground(new Color(0xE3DDF7));
        details.setAlignmentX(CENTER_ALIGNMENT);
        branding.add(details);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(32, 24, 32, 24);
        panel.add(branding, constraints);
        return panel;
    }

    private JPanel buildLogoBadge() {
        JPanel badge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillOval(3, 3, getWidth() - 6, getHeight() - 6);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setOpaque(false);
        Dimension badgeSize = new Dimension(132, 132);
        badge.setPreferredSize(badgeSize);
        badge.setMinimumSize(badgeSize);
        badge.setMaximumSize(badgeSize);

        java.net.URL logoUrl = LoginWindow.class.getResource(LOGO_RESOURCE);
        if (logoUrl == null) {
            throw new IllegalStateException("Logo TIKE introuvable : " + LOGO_RESOURCE);
        }
        ImageIcon logo = new ImageIcon(logoUrl);
        Image scaledLogo = logo.getImage().getScaledInstance(88, 88, Image.SCALE_SMOOTH);
        badge.add(new JLabel(new ImageIcon(scaledLogo)));
        badge.setAlignmentX(CENTER_ALIGNMENT);
        return badge;
    }

    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(0xFAF9FD));

        JPanel card = new JPanel(new BorderLayout(0, 30));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xEAE5F5)),
                new EmptyBorder(34, 34, 26, 34)));
        card.setPreferredSize(new Dimension(520, firstRun ? 620 : 520));
        card.add(buildHeader(), BorderLayout.NORTH);
        card.add(buildForm(), BorderLayout.CENTER);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(18, 18, 18, 18);
        panel.add(card, constraints);
        return panel;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 10));
        header.setOpaque(false);
        JLabel title = new JLabel(
                firstRun ? "Créer l'administrateur" : "Se connecter", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 34));
        title.setForeground(UiTheme.TEXT);
        JLabel subtitle = new JLabel(
                firstRun ? "Première configuration sécurisée de TIKE"
                        : "Accédez à votre espace de gestion des événements.",
                SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitle.setForeground(UiTheme.MUTED);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.setOpaque(false);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 17));
        fields.setOpaque(false);
        if (firstRun) {
            fields.add(UiComponents.formRow("Nom complet", nameField));
        }
        fields.add(UiComponents.formRow("Nom d'utilisateur", usernameField));
        JPanel passwordRow = new JPanel(new BorderLayout(8, 0));
        passwordRow.setOpaque(false);
        JButton visibility = new JButton("Afficher");
        visibility.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        visibility.setFocusPainted(false);
        char maskedCharacter = passwordField.getEchoChar();
        visibility.addActionListener(event -> {
            if (passwordField.getEchoChar() == (char) 0) {
                passwordField.setEchoChar(maskedCharacter);
                visibility.setText("Afficher");
            } else {
                passwordField.setEchoChar((char) 0);
                visibility.setText("Masquer");
            }
        });
        JPanel passwordInput = new JPanel(new BorderLayout(8, 0));
        passwordInput.setOpaque(false);
        passwordInput.add(passwordField, BorderLayout.CENTER);
        passwordInput.add(visibility, BorderLayout.EAST);
        passwordRow.add(UiComponents.formRow("Mot de passe", passwordInput), BorderLayout.CENTER);
        fields.add(passwordRow);
        if (firstRun) {
            fields.add(UiComponents.formRow("Confirmer le mot de passe", confirmationField));
        }

        JButton submit = UiComponents.primaryButton(firstRun ? "Créer le compte administrateur" : "Se connecter");
        submit.setPreferredSize(new Dimension(420, 52));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        submit.setAlignmentX(LEFT_ALIGNMENT);
        submit.addActionListener(event -> submit());
        feedback.setForeground(UiTheme.ERROR);
        feedback.setFont(UiTheme.FONT);
        feedback.setLineWrap(true);
        feedback.setWrapStyleWord(true);
        feedback.setEditable(false);
        feedback.setFocusable(false);
        feedback.setOpaque(false);
        feedback.setBorder(new EmptyBorder(8, 2, 8, 2));
        feedback.setPreferredSize(new Dimension(420, 56));
        feedback.setMinimumSize(new Dimension(300, 56));
        submit.setAlignmentX(LEFT_ALIGNMENT);
        fields.setAlignmentX(LEFT_ALIGNMENT);
        feedback.setAlignmentX(LEFT_ALIGNMENT);
        form.add(fields);
        form.add(javax.swing.Box.createVerticalStrut(18));
        form.add(submit);
        form.add(feedback);
        form.setBorder(new EmptyBorder(4, 0, 0, 0));
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
