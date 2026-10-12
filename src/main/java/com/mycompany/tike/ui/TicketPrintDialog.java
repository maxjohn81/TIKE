package com.mycompany.tike.ui;

import com.google.zxing.WriterException;
import com.mycompany.tike.model.Ticket;
import com.mycompany.tike.util.TicketPdfExporter;
import com.mycompany.tike.util.TicketQrCode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

final class TicketPrintDialog extends JDialog {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final List<Ticket> tickets;
    private final JComboBox<Ticket> ticketSelector = new JComboBox<>();
    private final JLabel eventName = new JLabel("", SwingConstants.CENTER);
    private final JLabel eventDate = new JLabel("", SwingConstants.CENTER);
    private final JLabel location = new JLabel("", SwingConstants.CENTER);
    private final JLabel category = new JLabel("", SwingConstants.CENTER);
    private final JLabel customer = new JLabel("", SwingConstants.CENTER);
    private final JLabel price = new JLabel("", SwingConstants.CENTER);
    private final JLabel status = new JLabel("", SwingConstants.CENTER);
    private final JLabel code = new JLabel("", SwingConstants.CENTER);
    private final JLabel qrImage = new JLabel("", SwingConstants.CENTER);

    static void show(Component parent, List<Ticket> tickets) {
        try {
            Window owner = SwingUtilities.getWindowAncestor(parent);
            TicketPrintDialog dialog = new TicketPrintDialog(owner, tickets);
            dialog.setVisible(true);
        } catch (WriterException error) {
            CatalogUiSupport.showFailure(parent, error);
        }
    }

    private TicketPrintDialog(Window owner, List<Ticket> tickets) throws WriterException {
        super(owner, "Aperçu et réimpression des billets", Dialog.ModalityType.APPLICATION_MODAL);
        if (tickets == null || tickets.isEmpty()) {
            throw new IllegalArgumentException("Aucun billet à afficher.");
        }
        this.tickets = List.copyOf(tickets);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        setMinimumSize(new Dimension(520, 650));
        pack();
        setLocationRelativeTo(owner);
        refreshPreview();
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel("Billets de la vente " + tickets.get(0).saleNumber());
        title.setFont(UiTheme.FONT_BOLD);
        title.setForeground(UiTheme.TEXT);
        heading.add(title, BorderLayout.NORTH);
        for (Ticket ticket : tickets) {
            ticketSelector.addItem(ticket);
        }
        ticketSelector.setRenderer((list, ticket, index, selected, focus) -> {
            JLabel label = new JLabel(ticket == null ? "" : "Billet " + (index + 1) + " — " + ticket.categoryName());
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        });
        if (tickets.size() > 1) {
            JPanel selectorRow = new JPanel(new BorderLayout(8, 0));
            selectorRow.add(new JLabel("Billet à prévisualiser :"), BorderLayout.WEST);
            selectorRow.add(ticketSelector, BorderLayout.CENTER);
            heading.add(selectorRow, BorderLayout.SOUTH);
        }
        ticketSelector.addActionListener(event -> refreshPreview());
        content.add(heading, BorderLayout.NORTH);
        content.add(buildTicketCard(), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton close = new JButton("Fermer");
        close.addActionListener(event -> dispose());
        JButton save = UiComponents.primaryButton("Enregistrer le PDF");
        save.addActionListener(event -> savePdf());
        actions.add(close);
        actions.add(save);
        content.add(actions, BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildTicketCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        JPanel detail = new JPanel(new java.awt.GridLayout(0, 1, 0, 5));
        detail.setOpaque(false);
        eventName.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 20));
        eventName.setForeground(UiTheme.PRIMARY_DARK);
        eventDate.setForeground(UiTheme.TEXT);
        location.setForeground(UiTheme.TEXT);
        category.setForeground(UiTheme.TEXT);
        customer.setForeground(UiTheme.TEXT);
        price.setFont(UiTheme.FONT_BOLD);
        price.setForeground(UiTheme.TEXT);
        status.setForeground(UiTheme.MUTED);
        detail.add(eventName);
        detail.add(eventDate);
        detail.add(location);
        detail.add(category);
        detail.add(customer);
        detail.add(price);
        detail.add(status);
        card.add(detail, BorderLayout.NORTH);
        JPanel qrPanel = new JPanel(new BorderLayout(0, 4));
        qrPanel.setOpaque(false);
        qrPanel.add(qrImage, BorderLayout.CENTER);
        code.setFont(new java.awt.Font("Consolas", java.awt.Font.PLAIN, 12));
        code.setForeground(UiTheme.TEXT);
        qrPanel.add(code, BorderLayout.SOUTH);
        card.add(qrPanel, BorderLayout.CENTER);
        return card;
    }

    private void refreshPreview() {
        Ticket selected = (Ticket) ticketSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        eventName.setText(selected.eventName());
        eventDate.setText(selected.eventStartsAt() == null
                ? "Date non renseignée"
                : DATE_FORMAT.format(selected.eventStartsAt()));
        location.setText("Lieu : " + selected.location());
        category.setText("Catégorie : " + selected.categoryName());
        customer.setText("Titulaire : " + selected.customerName());
        price.setText("Prix : " + selected.price().toPlainString() + " Ar");
        status.setText("Statut du billet : " + statusLabel(selected.status()));
        code.setText(selected.uniqueCode());
        try {
            qrImage.setIcon(new ImageIcon(TicketQrCode.create(selected.uniqueCode())));
            qrImage.setPreferredSize(new Dimension(190, 190));
            qrImage.setText("");
        } catch (WriterException error) {
            qrImage.setIcon(null);
            qrImage.setText("QR Code indisponible");
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void savePdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Enregistrer les billets en PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("Document PDF (*.pdf)", "pdf"));
        chooser.setSelectedFile(new java.io.File(tickets.get(0).saleNumber() + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            destination = destination.resolveSibling(destination.getFileName() + ".pdf");
        }
        if (Files.exists(destination) && !UiComponents.confirm(
                this, "Le fichier existe déjà. Voulez-vous le remplacer ?")) {
            return;
        }
        try {
            TicketPdfExporter.export(tickets, destination);
            UiComponents.showResult(this,
                    tickets.size() + " billet(s) exporté(s) dans :\n" + destination.toAbsolutePath(), true);
        } catch (IOException | RuntimeException error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private static String statusLabel(String ticketStatus) {
        return switch (ticketStatus) {
            case "VALIDE" -> "Valide";
            case "UTILISE" -> "Déjà utilisé";
            case "ANNULE" -> "Annulé";
            case "REMBOURSE" -> "Remboursé";
            default -> ticketStatus;
        };
    }
}
