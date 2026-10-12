package com.mycompany.tike.ui;

import com.mycompany.tike.model.AccessControlEvent;
import com.mycompany.tike.model.AccessControlOutcome;
import com.mycompany.tike.model.AccessControlResult;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.AccessControlService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public final class AccessControlPanel extends JPanel {
    private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final UserSession session;
    private final AccessControlService service;
    private final JComboBox<AccessControlEvent> events = new JComboBox<>();
    private final JTextField ticketCode = CatalogUiSupport.textField("");
    private final JLabel resultTitle = new JLabel("En attente d'un contrôle", SwingConstants.CENTER);
    private final JLabel resultDetails = new JLabel("Sélectionnez un événement puis saisissez le code du billet.",
            SwingConstants.CENTER);
    private final JPanel resultCard = new JPanel(new BorderLayout(0, 10));

    public AccessControlPanel(UserSession session, AccessControlService service) {
        this.session = session;
        this.service = service;
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        add(UiComponents.pageHeading(
                "Contrôle d'accès", "Saisissez manuellement le code imprimé ou lu sur le billet."),
                BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        content.add(buildForm(), BorderLayout.NORTH);
        content.add(buildResultCard(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        refreshEvents();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 16));
        form.setOpaque(false);
        JPanel eventRow = new JPanel(new BorderLayout(10, 0));
        eventRow.setOpaque(false);
        eventRow.add(UiComponents.formRow("Événement à contrôler *", events), BorderLayout.CENTER);
        JButton refresh = new JButton("Actualiser");
        refresh.addActionListener(event -> refreshEvents());
        eventRow.add(refresh, BorderLayout.EAST);
        form.add(eventRow);

        ticketCode.setToolTipText("UUID figurant sous le QR Code du billet");
        ticketCode.setFont(new Font("Consolas", Font.PLAIN, 16));
        ticketCode.addActionListener(event -> checkTicket());
        form.add(UiComponents.formRow("Code du billet *", ticketCode));

        JPanel action = new JPanel(new FlowLayout(FlowLayout.CENTER));
        action.setOpaque(false);
        JButton check = UiComponents.primaryButton("Vérifier l'accès");
        check.addActionListener(event -> checkTicket());
        action.add(check);
        form.add(action);
        return form;
    }

    private JPanel buildResultCard() {
        resultCard.setBackground(UiTheme.SURFACE);
        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)));
        resultTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        resultTitle.setForeground(UiTheme.MUTED);
        resultDetails.setForeground(UiTheme.TEXT);
        resultCard.add(resultTitle, BorderLayout.NORTH);
        resultCard.add(resultDetails, BorderLayout.CENTER);
        return resultCard;
    }

    private void refreshEvents() {
        try {
            List<AccessControlEvent> available = service.listEvents(session);
            events.removeAllItems();
            for (AccessControlEvent event : available) {
                events.addItem(event);
            }
            if (available.isEmpty()) {
                resultTitle.setText("Aucun événement à contrôler");
                resultTitle.setForeground(UiTheme.MUTED);
                resultDetails.setText("Il n'y a actuellement aucun événement programmé, en vente ou complet.");
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void checkTicket() {
        AccessControlEvent selected = (AccessControlEvent) events.getSelectedItem();
        if (selected == null) {
            UiComponents.showResult(this, "Aucun événement n'est disponible pour le contrôle.", false);
            return;
        }
        try {
            AccessControlResult result = service.check(session, selected.id(), ticketCode.getText());
            displayResult(result);
            ticketCode.selectAll();
            ticketCode.requestFocusInWindow();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
            ticketCode.requestFocusInWindow();
        }
    }

    private void displayResult(AccessControlResult result) {
        boolean accepted = result.outcome() == AccessControlOutcome.VALIDE;
        resultTitle.setText(result.outcome().label());
        resultTitle.setForeground(accepted ? new Color(0x198754) : UiTheme.ERROR);
        StringBuilder details = new StringBuilder("<html>");
        details.append("Événement contrôlé : <b>")
                .append(escape(result.eventName())).append("</b><br>");
        if (result.ticketEventName() != null) {
            details.append("Événement du billet : <b>")
                    .append(escape(result.ticketEventName())).append("</b><br>");
        }
        if (result.categoryName() != null) {
            details.append("Catégorie : ").append(escape(result.categoryName())).append("<br>");
        }
        if (result.customerName() != null) {
            details.append("Titulaire : ").append(escape(result.customerName())).append("<br>");
        }
        if (result.ticketCode() != null) {
            details.append("Code : ").append(escape(result.ticketCode())).append("<br>");
        }
        if (result.checkedAt() != null) {
            details.append("Contrôle : ").append(CHECK_TIME.format(result.checkedAt()));
        }
        details.append("</html>");
        resultDetails.setText(details.toString());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
