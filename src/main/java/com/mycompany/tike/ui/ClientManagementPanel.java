package com.mycompany.tike.ui;

import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.SaleHistoryItem;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.SalesService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public final class ClientManagementPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final UserSession session;
    private final SalesService service;
    private final ClientTableModel model = new ClientTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = CatalogUiSupport.textField("");

    public ClientManagementPanel(UserSession session, SalesService service) {
        this.session = session;
        this.service = service;
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        add(UiComponents.pageHeading("Clients", "Recherchez les clients et consultez leur historique d'achats."),
                BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 0, 0, 0));
        body.add(buildToolbar(), BorderLayout.NORTH);
        body.add(UiComponents.styleTable(table), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        refresh();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);
        search.setToolTipText("Rechercher par nom, téléphone ou e-mail");
        search.addActionListener(event -> refresh());
        toolbar.add(search, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton find = new JButton("Rechercher");
        JButton add = UiComponents.primaryButton("Ajouter un client");
        JButton edit = new JButton("Modifier");
        JButton history = new JButton("Historique");
        find.addActionListener(event -> refresh());
        add.addActionListener(event -> editClient(null));
        edit.addActionListener(event -> {
            Client client = selected();
            if (client == null) {
                UiComponents.showResult(this, "Sélectionnez un client à modifier.", false);
            } else {
                editClient(client);
            }
        });
        history.addActionListener(event -> showHistory());
        actions.add(find);
        actions.add(add);
        actions.add(edit);
        actions.add(history);
        toolbar.add(actions, BorderLayout.EAST);
        return toolbar;
    }

    private Client selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.get(table.convertRowIndexToModel(row));
    }

    private void refresh() {
        try {
            model.setRows(service.listClients(session, search.getText()));
            table.clearSelection();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void editClient(Client current) {
        JTextField name = CatalogUiSupport.textField(current == null ? "" : current.fullName());
        JTextField phone = CatalogUiSupport.textField(current == null ? "" : current.phone());
        JTextField email = CatalogUiSupport.textField(current == null ? "" : current.email());
        JPanel form = CatalogUiSupport.form(
                UiComponents.formRow("Nom complet *", name),
                UiComponents.formRow("Téléphone (facultatif)", phone),
                UiComponents.formRow("E-mail (facultatif)", email));
        if (!CatalogUiSupport.confirmForm(
                this, current == null ? "Ajouter un client" : "Modifier le client", form)) {
            return;
        }
        try {
            Client saved = service.saveClient(session, new Client(
                    current == null ? 0 : current.id(), name.getText(), phone.getText(), email.getText()));
            UiComponents.showResult(this, "Client enregistré (ID " + saved.id() + ").", true);
            refresh();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void showHistory() {
        Client client = selected();
        if (client == null) {
            UiComponents.showResult(this, "Sélectionnez un client pour afficher son historique.", false);
            return;
        }
        try {
            List<SaleHistoryItem> history = service.clientHistory(session, client.id());
            HistoryTableModel historyModel = new HistoryTableModel(history);
            JTable historyTable = new JTable(historyModel);
            historyTable.setAutoCreateRowSorter(true);
            javax.swing.JScrollPane scroll = UiComponents.styleTable(historyTable);
            scroll.setPreferredSize(new java.awt.Dimension(720, 360));
            javax.swing.JOptionPane.showMessageDialog(
                    this, scroll, "Historique de " + client.fullName(), javax.swing.JOptionPane.PLAIN_MESSAGE);
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private static final class ClientTableModel extends DefaultTableModel {
        private List<Client> rows = new ArrayList<>();

        private ClientTableModel() {
            super(new Object[]{"ID", "Nom complet", "Téléphone", "E-mail"}, 0);
        }

        private Client get(int row) {
            return rows.get(row);
        }

        private void setRows(List<Client> clients) {
            rows = List.copyOf(clients);
            setRowCount(0);
            for (Client client : rows) {
                addRow(new Object[]{client.id(), client.fullName(), client.phone(), client.email()});
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static final class HistoryTableModel extends DefaultTableModel {
        private HistoryTableModel(List<SaleHistoryItem> history) {
            super(new Object[]{"Vente", "Événement", "Date", "Montant", "Statut", "Billets"}, 0);
            for (SaleHistoryItem item : history) {
                addRow(new Object[]{
                    item.saleNumber(),
                    item.eventName(),
                    item.soldAt() == null ? "" : DATE_FORMAT.format(item.soldAt()),
                    item.total().toPlainString() + " Ar",
                    item.status(),
                    item.ticketCount()
                });
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
