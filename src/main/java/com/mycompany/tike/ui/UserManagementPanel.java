package com.mycompany.tike.ui;

import com.mycompany.tike.model.User;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.AuthenticationService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.util.Arrays;
import javax.swing.border.EmptyBorder;

public final class UserManagementPanel extends JPanel {
    private final UserSession session;
    private final AuthenticationService authenticationService;
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"ID", "Nom complet", "Nom d'utilisateur", "Rôle", "État"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private List<User> users = List.of();

    public UserManagementPanel(UserSession session, AuthenticationService authenticationService) {
        this.session = session;
        this.authenticationService = authenticationService;
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);
        add(buildActions(), BorderLayout.NORTH);
        add(UiComponents.styleTable(table), BorderLayout.CENTER);
        refresh();
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        actions.setOpaque(false);
        JButton add = UiComponents.primaryButton("Ajouter un utilisateur");
        JButton edit = new JButton("Modifier");
        JButton deactivate = new JButton("Désactiver");
        add.addActionListener(event -> openEditor(null));
        edit.addActionListener(event -> {
            User selected = selectedUser();
            if (selected != null) {
                openEditor(selected);
            }
        });
        deactivate.addActionListener(event -> deactivateSelected());
        actions.add(add);
        actions.add(edit);
        actions.add(deactivate);
        return actions;
    }

    private User selectedUser() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Sélectionnez un utilisateur.", "Information",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return users.get(table.convertRowIndexToModel(row));
    }

    private void openEditor(User selected) {
        UserEditor editor = new UserEditor(selected);
        JOptionPane pane = new JOptionPane(
                editor,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(
                this,
                selected == null ? "Nouvel utilisateur" : "Modifier l'utilisateur");
        dialog.setResizable(true);
        dialog.setMinimumSize(new java.awt.Dimension(500, selected == null ? 560 : 620));
        dialog.setSize(520, selected == null ? 580 : 640);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        Object resultValue = pane.getValue();
        int result = resultValue instanceof Integer ? (Integer) resultValue : JOptionPane.CLOSED_OPTION;
        if (result != JOptionPane.OK_OPTION) {
            editor.clearPasswords();
            return;
        }
        char[] password = editor.password();
        char[] confirmation = editor.confirmation();
        try {
            if (selected == null) {
                authenticationService.createUser(
                        session,
                        editor.fullName(),
                        editor.username(),
                        editor.role(),
                        password,
                        confirmation);
            } else {
                User updated = new User(
                        selected.getId(),
                        editor.fullName(),
                        editor.username(),
                        editor.role(),
                        editor.active());
                authenticationService.updateUser(session, updated, password, confirmation);
            }
            refresh();
        } catch (SQLException | IllegalArgumentException | SecurityException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Impossible d'enregistrer l'utilisateur",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            editor.clearPasswords();
        }
    }

    private void deactivateSelected() {
        User selected = selectedUser();
        if (selected == null || !selected.isActive()) {
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this,
                "Désactiver le compte « " + selected.getUsername() + " » ?",
                "Confirmer la désactivation",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            authenticationService.deactivateUser(session, selected.getId());
            refresh();
        } catch (SQLException | IllegalArgumentException | SecurityException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Impossible de désactiver l'utilisateur",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refresh() {
        try {
            users = authenticationService.listUsers(session);
            tableModel.setRowCount(0);
            for (User user : users) {
                tableModel.addRow(new Object[] {
                    user.getId(),
                    user.getFullName(),
                    user.getUsername(),
                    user.getRole().getLabel(),
                    user.isActive() ? "Actif" : "Désactivé"
                });
            }
        } catch (SQLException | SecurityException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Impossible de charger les utilisateurs",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class UserEditor extends JPanel {
        private final JTextField fullName = new JTextField();
        private final JTextField username = new JTextField();
        private final JComboBox<UserRole> role = new JComboBox<>(UserRole.values());
        private final JCheckBox active = new JCheckBox("Compte actif");
        private final JPasswordField password = new JPasswordField();
        private final JPasswordField confirmation = new JPasswordField();

        private UserEditor(User existing) {
            super(new BorderLayout(0, 12));
            setPreferredSize(new java.awt.Dimension(460, existing == null ? 430 : 490));
            setBorder(new EmptyBorder(8, 8, 8, 8));
            JPanel fields = new JPanel(new GridLayout(0, 1, 0, 10));
            fields.setOpaque(false);
            fields.add(UiComponents.formRow("Nom complet", fullName));
            fields.add(UiComponents.formRow("Nom d'utilisateur", username));
            fields.add(UiComponents.formRow("Rôle", role));
            role.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
                JLabel label = new JLabel(value == null ? "" : ((UserRole) value).getLabel());
                label.setOpaque(true);
                label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            });
            if (existing != null) {
                fields.add(active);
                password.setToolTipText("Laisser vide pour conserver le mot de passe actuel.");
            } else {
                password.setToolTipText("Au moins 10 caractères.");
            }
            fields.add(UiComponents.formRow(
                    existing == null ? "Mot de passe" : "Nouveau mot de passe (facultatif)", password));
            fields.add(UiComponents.formRow("Confirmer le mot de passe", confirmation));
            add(fields, BorderLayout.NORTH);
            setBackground(UiTheme.SURFACE);
            if (existing != null) {
                fullName.setText(existing.getFullName());
                username.setText(existing.getUsername());
                role.setSelectedItem(existing.getRole());
                active.setSelected(existing.isActive());
            } else {
                active.setSelected(true);
            }
        }

        private String fullName() { return fullName.getText(); }
        private String username() { return username.getText(); }
        private UserRole role() { return (UserRole) role.getSelectedItem(); }
        private boolean active() { return active.isSelected(); }
        private char[] password() { return password.getPassword(); }
        private char[] confirmation() { return confirmation.getPassword(); }
        private void clearPasswords() {
            password.setText("");
            confirmation.setText("");
        }
    }
}
