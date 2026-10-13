package com.mycompany.tike.ui;

import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleHistoryItem;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.Ticket;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.RefundResult;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.CatalogService;
import com.mycompany.tike.service.RefundService;
import com.mycompany.tike.service.SalesService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

public final class SalesPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.forLanguageTag("fr-FR"));
    private final UserSession session;
    private final CatalogService catalogService;
    private final SalesService salesService;
    private final RefundService refundService;
    private final JComboBox<EventChoice> events = new JComboBox<>();
    private final JComboBox<CategoryChoice> categories = new JComboBox<>();
    private final JComboBox<ClientChoice> clients = new JComboBox<>();
    private final JComboBox<PaymentMethod> paymentMethod = new JComboBox<>();
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField paymentReference = CatalogUiSupport.textField("");
    private final JTextField ticketSaleNumber = CatalogUiSupport.textField("");
    private final JTextField refundSaleNumber = CatalogUiSupport.textField("");
    private final SaleSearchPicker reprintSalePicker = new SaleSearchPicker(ticketSaleNumber::setText);
    private final SaleSearchPicker refundSalePicker = new SaleSearchPicker(refundSaleNumber::setText);
    private final JTextField refundReason = CatalogUiSupport.textField("");
    private final CartTableModel cartModel = new CartTableModel();
    private final JTable cartTable = new JTable(cartModel);
    private final SalesTableModel salesModel = new SalesTableModel();
    private final JTable salesTable = new JTable(salesModel);
    private final JLabel totalLabel = new JLabel();
    private boolean refreshingEvents;

    public SalesPanel(
            UserSession session, CatalogService catalogService, SalesService salesService, RefundService refundService) {
        this.session = session;
        this.catalogService = catalogService;
        this.salesService = salesService;
        this.refundService = refundService;
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        add(UiComponents.pageHeading(
                "Ventes", "Sélectionnez un événement, composez le panier et enregistrez l'encaissement."),
                BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        body.add(buildSaleForm(), BorderLayout.NORTH);
        body.add(buildCart(), BorderLayout.CENTER);
        JPanel lower = new JPanel();
        lower.setOpaque(false);
        lower.setLayout(new javax.swing.BoxLayout(lower, javax.swing.BoxLayout.Y_AXIS));
        JPanel actions = buildBottomActions();
        actions.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
        lower.add(actions);
        lower.add(javax.swing.Box.createVerticalStrut(14));
        JPanel salesHistory = buildSalesHistory();
        salesHistory.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        salesHistory.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        lower.add(salesHistory);
        body.add(lower, BorderLayout.SOUTH);
        JScrollPane pageScroll = new JScrollPane(
                body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        pageScroll.setOpaque(false);
        pageScroll.getViewport().setOpaque(false);
        add(pageScroll, BorderLayout.CENTER);
        events.addActionListener(event -> refreshCategories());
        paymentMethod.addItem(null);
        for (PaymentMethod method : PaymentMethod.values()) {
            paymentMethod.addItem(method);
        }
        paymentMethod.setSelectedIndex(0);
        paymentMethod.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "Sélectionnez un mode de paiement" : value.getLabel());
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        refreshEvents();
        refreshClients();
        refreshSales();
        updateTotal();
    }

    private JPanel buildSaleForm() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 10));
        form.setOpaque(false);

        JPanel formHeading = new JPanel(new BorderLayout());
        formHeading.setOpaque(false);
        JLabel formTitle = new JLabel("Nouvelle vente");
        formTitle.setFont(UiTheme.FONT_BOLD);
        formTitle.setForeground(UiTheme.TEXT);
        JButton reset = new JButton("Vider les champs");
        reset.addActionListener(event -> resetSaleForm());
        formHeading.add(formTitle, BorderLayout.WEST);
        formHeading.add(reset, BorderLayout.EAST);
        form.add(formHeading);

        JPanel eventRow = new JPanel(new BorderLayout(8, 0));
        eventRow.setOpaque(false);
        eventRow.add(UiComponents.formRow("Événement en vente *", events), BorderLayout.CENTER);
        JButton refreshEvents = new JButton("Actualiser");
        refreshEvents.addActionListener(event -> refreshEvents());
        eventRow.add(refreshEvents, BorderLayout.EAST);
        form.add(eventRow);

        JPanel categoryRow = new JPanel(new BorderLayout(8, 0));
        categoryRow.setOpaque(false);
        categoryRow.add(UiComponents.formRow("Catégorie *", categories), BorderLayout.CENTER);
        categoryRow.add(UiComponents.formRow("Quantité", quantity), BorderLayout.EAST);
        JButton add = UiComponents.primaryButton("Ajouter au panier");
        add.addActionListener(event -> addCategory());
        categoryRow.add(add, BorderLayout.SOUTH);
        form.add(categoryRow);

        JPanel clientPayment = new JPanel(new java.awt.GridLayout(1, 3, 12, 0));
        clientPayment.setOpaque(false);
        JPanel clientField = new JPanel(new BorderLayout(5, 0));
        clientField.setOpaque(false);
        JPanel clientRow = UiComponents.formRow("Client (facultatif)", clients);
        clients.setPreferredSize(new java.awt.Dimension(150, 40));
        clientField.add(clientRow, BorderLayout.CENTER);
        JButton newClient = new JButton("Nouveau client");
        newClient.addActionListener(event -> createClient());
        clientField.add(newClient, BorderLayout.SOUTH);
        clientPayment.add(clientField);
        JPanel paymentRow = UiComponents.formRow("Mode de paiement *", paymentMethod);
        paymentMethod.setPreferredSize(new java.awt.Dimension(150, 40));
        clientPayment.add(paymentRow);
        JPanel referenceRow = UiComponents.formRow("Référence (facultative)", paymentReference);
        paymentReference.setPreferredSize(new java.awt.Dimension(150, 40));
        clientPayment.add(referenceRow);
        form.add(clientPayment);

        JLabel notice = new JLabel(
                "<html>Les modes électroniques sont enregistrés à titre déclaratif : TIKE ne déclenche aucun paiement. "
                        + "Confirmez la vente uniquement après avoir vérifié l'encaissement.</html>");
        notice.setForeground(UiTheme.MUTED);
        form.add(notice);
        return form;
    }

    private JPanel buildCart() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Panier de vente");
        title.setFont(UiTheme.FONT_BOLD);
        title.setForeground(UiTheme.TEXT);
        heading.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton remove = new JButton("Retirer");
        remove.addActionListener(event -> removeSelected());
        JButton clear = new JButton("Vider le panier");
        clear.addActionListener(event -> clearCart());
        actions.add(remove);
        actions.add(clear);
        heading.add(actions, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        JScrollPane tableScroll = UiComponents.styleTable(cartTable);
        panel.add(tableScroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        totalLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 21));
        totalLabel.setForeground(UiTheme.PRIMARY_DARK);
        footer.add(totalLabel, BorderLayout.WEST);
        JButton confirm = UiComponents.primaryButton("Confirmer la vente");
        confirm.addActionListener(event -> confirmSale());
        footer.add(confirm, BorderLayout.EAST);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTicketLookup() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Billets et réimpression"));
        reprintSalePicker.setToolTipText("Rechercher par nom du client, événement ou numéro de vente");
        panel.add(reprintSalePicker, BorderLayout.NORTH);
        ticketSaleNumber.setToolTipText("Saisissez le numéro de vente, par exemple VT-...");
        ticketSaleNumber.addActionListener(event -> findTickets());
        panel.add(UiComponents.formRow("Numéro de vente", ticketSaleNumber), BorderLayout.CENTER);
        JButton find = new JButton("Afficher / réimprimer");
        find.addActionListener(event -> findTickets());
        panel.add(find, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildBottomActions() {
        JPanel actions = new JPanel(new java.awt.GridLayout(0, 1, 0, 12));
        actions.setOpaque(false);
        actions.add(buildTicketLookup());
        if (session.getRole() == UserRole.ADMIN) {
            actions.add(buildRefundForm());
        }
        return actions;
    }

    private JPanel buildSalesHistory() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        JLabel heading = new JLabel("Toutes les ventes enregistrées");
        heading.setFont(UiTheme.FONT_BOLD);
        heading.setForeground(UiTheme.TEXT);
        JButton refresh = new JButton("Actualiser");
        refresh.addActionListener(event -> refreshSales());
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(heading, BorderLayout.WEST);
        titleRow.add(refresh, BorderLayout.EAST);
        panel.add(titleRow, BorderLayout.NORTH);
        salesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        salesTable.setFillsViewportHeight(true);
        setMinimumSalesColumnWidths();
        JScrollPane tableScroll = UiComponents.styleTable(salesTable);
        tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setPreferredSize(new java.awt.Dimension(0, 220));
        tableScroll.setMinimumSize(new java.awt.Dimension(0, 180));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private void setMinimumSalesColumnWidths() {
        int[] minimumWidths = {75, 70, 85, 80, 75, 50};
        for (int index = 0; index < minimumWidths.length; index++) {
            salesTable.getColumnModel().getColumn(index).setMinWidth(minimumWidths[index]);
        }
    }

    private void refreshSales() {
        try {
            List<SaleHistoryItem> sales = salesService.listSales(session);
            salesModel.setSales(sales);
            reprintSalePicker.setSales(sales, "PAYEE");
            refundSalePicker.setSales(sales, "PAYEE");
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private JPanel buildRefundForm() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Remboursement intégral — Administrateur"));
        JPanel fields = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        fields.setOpaque(false);
        refundSalePicker.setToolTipText("Rechercher par nom du client, événement ou numéro de vente");
        fields.add(refundSalePicker);
        refundSaleNumber.setToolTipText("Numéro de la vente à rembourser");
        refundReason.setToolTipText("Motif obligatoire du remboursement");
        fields.add(UiComponents.formRow("Numéro de vente sélectionné", refundSaleNumber));
        fields.add(UiComponents.formRow("Motif *", refundReason));
        panel.add(fields, BorderLayout.CENTER);
        JButton refund = new JButton("Enregistrer le remboursement");
        refund.addActionListener(event -> refundSale());
        panel.add(refund, BorderLayout.EAST);
        JLabel notice = new JLabel(
                "TIKE n'effectue pas le transfert : procédez au remboursement externe avant de l'enregistrer ici.");
        notice.setForeground(UiTheme.MUTED);
        panel.add(notice, BorderLayout.SOUTH);
        return panel;
    }

    private void refundSale() {
        if (!UiComponents.confirm(this,
                "<html>Confirmez-vous que le remboursement intégral a déjà été effectué hors de TIKE ?<br>"
                        + "Cette opération invalidera tous les billets de cette vente et ne pourra pas être annulée.</html>")) {
            return;
        }
        try {
            RefundResult refund = refundService.refundFullSale(
                    session, refundSaleNumber.getText(), refundReason.getText());
            UiComponents.showResult(this,
                    "Remboursement intégral enregistré.\nVente : " + refund.saleNumber()
                            + "\nRéférence : " + refund.refundNumber()
                            + "\nMontant : " + money(refund.amount()) + " Ar"
                            + "\nBillets invalidés : " + refund.ticketCount()
                            + "\nLe stock correspondant est de nouveau disponible.",
                    true);
            refundReason.setText("");
            refundSaleNumber.setText("");
            refreshSales();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void refreshEvents() {
        try {
            refreshingEvents = true;
            events.removeAllItems();
            events.addItem(new EventChoice(null));
            List<Event> onSale = catalogService.listEvents(session, "", EventStatus.EN_VENTE);
            for (Event event : onSale) {
                events.addItem(new EventChoice(event));
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        } finally {
            refreshingEvents = false;
        }
        refreshCategories();
    }

    private void refreshCategories() {
        if (refreshingEvents) {
            return;
        }
        EventChoice choice = (EventChoice) events.getSelectedItem();
        Event selectedEvent = choice == null ? null : choice.event();
        List<TicketCategory> available = List.of();
        try {
            categories.removeAllItems();
            if (selectedEvent == null) {
                categories.addItem(new CategoryChoice(null));
            } else {
                available = catalogService.listCategories(session, selectedEvent.id());
                for (TicketCategory category : available) {
                    categories.addItem(new CategoryChoice(category));
                }
                if (available.isEmpty()) {
                    categories.addItem(new CategoryChoice(null));
                }
            }
            categories.setRenderer((list, value, index, selected, focus) -> {
                JLabel label = new JLabel(value == null ? "" : value.display());
                label.setOpaque(true);
                label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                return label;
            });
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void refreshClients() {
        try {
            clients.removeAllItems();
            clients.addItem(new ClientChoice(null));
            for (Client client : salesService.listClients(session, "")) {
                clients.addItem(new ClientChoice(client));
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void addCategory() {
        CategoryChoice choice = (CategoryChoice) categories.getSelectedItem();
        if (choice == null || choice.category() == null) {
            UiComponents.showResult(this, "Sélectionnez un événement avec au moins une catégorie.", false);
            return;
        }
        EventChoice eventChoice = (EventChoice) events.getSelectedItem();
        Event event = eventChoice == null ? null : eventChoice.event();
        if (event == null) {
            UiComponents.showResult(this, "Aucun événement en vente n'est disponible.", false);
            return;
        }
        int count = (Integer) quantity.getValue();
        TicketCategory category = choice.category();
        int existingIndex = cartModel.indexOf(category.id());
        int currentCount = existingIndex < 0 ? 0 : cartModel.quantityAt(existingIndex);
        if ((long) currentCount + count > category.availableQuantity()) {
            UiComponents.showResult(this,
                    "La quantité demandée dépasse le stock disponible (" + category.availableQuantity() + ").", false);
            return;
        }
        if (existingIndex < 0) {
            cartModel.add(category, count);
        } else {
            cartModel.setQuantity(existingIndex, currentCount + count);
        }
        events.setEnabled(false);
        updateTotal();
    }

    private void removeSelected() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            UiComponents.showResult(this, "Sélectionnez une ligne du panier à retirer.", false);
            return;
        }
        cartModel.removeEntry(cartTable.convertRowIndexToModel(row));
        if (cartModel.getRowCount() == 0) {
            events.setEnabled(true);
        }
        updateTotal();
    }

    private void clearCart() {
        cartModel.clear();
        events.setEnabled(true);
        updateTotal();
    }

    private void resetSaleForm() {
        clearCart();
        events.setEnabled(true);
        if (events.getItemCount() > 0) {
            events.setSelectedIndex(0);
        }
        if (categories.getItemCount() > 0) {
            categories.setSelectedIndex(0);
        }
        if (clients.getItemCount() > 0) {
            clients.setSelectedIndex(0);
        }
        quantity.setValue(1);
        paymentMethod.setSelectedIndex(0);
        paymentReference.setText("");
        ticketSaleNumber.setText("");
    }

    private void updateTotal() {
        totalLabel.setText("Total : " + money(cartModel.total()) + " Ar");
    }

    private void createClient() {
        JTextField name = CatalogUiSupport.textField("");
        JTextField phone = CatalogUiSupport.textField("");
        JTextField email = CatalogUiSupport.textField("");
        JPanel form = CatalogUiSupport.form(
                UiComponents.formRow("Nom complet *", name),
                UiComponents.formRow("Téléphone (facultatif)", phone),
                UiComponents.formRow("E-mail (facultatif)", email));
        if (!CatalogUiSupport.confirmForm(this, "Nouveau client", form)) {
            return;
        }
        try {
            Client client = salesService.saveClient(session, new Client(0, name.getText(), phone.getText(), email.getText()));
            refreshClients();
            for (int i = 0; i < clients.getItemCount(); i++) {
                ClientChoice choice = clients.getItemAt(i);
                if (choice.client() != null && choice.client().id() == client.id()) {
                    clients.setSelectedIndex(i);
                    break;
                }
            }
            UiComponents.showResult(this, "Client enregistré (ID " + client.id() + ").", true);
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void confirmSale() {
        if (cartModel.getRowCount() == 0) {
            UiComponents.showResult(this, "Ajoutez au moins une catégorie au panier.", false);
            return;
        }
        PaymentMethod selectedPaymentMethod = (PaymentMethod) paymentMethod.getSelectedItem();
        if (selectedPaymentMethod == null) {
            UiComponents.showResult(this, "Sélectionnez un mode de paiement.", false);
            return;
        }
        EventChoice selectedEvent = (EventChoice) events.getSelectedItem();
        if (selectedEvent == null || selectedEvent.event() == null) {
            UiComponents.showResult(this, "Sélectionnez un événement en vente.", false);
            return;
        }
        ClientChoice selectedClient = (ClientChoice) clients.getSelectedItem();
        String paymentName = selectedPaymentMethod.getLabel();
        String confirmation = "<html>Événement : <b>" + selectedEvent.event().name() + "</b><br>"
                + "Billets : <b>" + cartModel.ticketCount() + "</b><br>"
                + "Total : <b>" + money(cartModel.total()) + " Ar</b><br>"
                + (cartModel.total().signum() == 0
                        ? "<br>Cette vente est gratuite ; aucun paiement ne sera enregistré."
                        : "Mode : <b>" + paymentName + "</b><br><br>"
                                + "Confirmez-vous que le paiement a été reçu ?<br>"
                                + "TIKE enregistrera la vente, mais n'effectuera pas le paiement.")
                + "</html>";
        if (!UiComponents.confirm(this, confirmation)) {
            return;
        }
        SaleResult sale;
        try {
            List<SaleLineRequest> lines = cartModel.toRequests();
            Long clientId = selectedClient == null || selectedClient.client() == null
                    ? null : selectedClient.client().id();
            sale = salesService.createSale(session, new SaleRequest(
                    selectedEvent.event().id(), clientId, lines, cartModel.total(),
                    selectedPaymentMethod, paymentReference.getText()));
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
            refreshCategories();
            return;
        }
        resetSaleForm();
        refreshEvents();
        refreshSales();
        try {
            List<Ticket> tickets = salesService.findPaidSaleTickets(session, sale.number());
            TicketPrintDialog.show(this, tickets);
        } catch (Exception ticketError) {
            String detail = ticketError.getMessage() == null ? "" : "\nDétail : " + ticketError.getMessage();
            UiComponents.showResult(this,
                    "La vente " + sale.number() + " est enregistrée (" + money(sale.total())
                            + " Ar), mais ses billets n'ont pas pu être chargés.\n"
                            + "Vous pourrez les retrouver avec le numéro de vente." + detail,
                    false);
        }
    }

    private void findTickets() {
        try {
            List<Ticket> tickets = salesService.findPaidSaleTickets(session, ticketSaleNumber.getText());
            TicketPrintDialog.show(this, tickets);
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private static String money(BigDecimal value) {
        return MONEY.format(value);
    }

    private final class SalesTableModel extends DefaultTableModel {
        private SalesTableModel() {
            super(new Object[] {"N° vente", "Client", "Événement", "Date", "Montant (Ar)", "Statut", "Billets"}, 0);
        }

        private void setSales(List<SaleHistoryItem> sales) {
            setRowCount(0);
            for (SaleHistoryItem sale : sales) {
                addRow(new Object[] {
                    sale.saleNumber(),
                    sale.clientName(),
                    sale.eventName(),
                    sale.soldAt() == null ? "" : DATE_FORMAT.format(sale.soldAt()),
                    money(sale.total()) + " Ar",
                    sale.status(),
                    sale.ticketCount()
                });
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private record EventChoice(Event event) {
        @Override
        public String toString() {
            if (event == null) {
                return "Sélectionnez un événement";
            }
            String date = event.startsAt() == null ? "" : DATE_FORMAT.format(event.startsAt());
            return event.name() + " — " + date;
        }
    }

    private record CategoryChoice(TicketCategory category) {
        private String display() {
            if (category == null) {
                return "Aucune catégorie disponible";
            }
            return category.name() + " — " + money(category.price()) + " Ar — "
                    + category.availableQuantity() + " disponible(s)";
        }
    }

    private record ClientChoice(Client client) {
        @Override
        public String toString() {
            return client == null ? "Client non renseigné" : client.toString();
        }
    }

    private static final class CartTableModel extends DefaultTableModel {
        private final List<CartEntry> entries = new ArrayList<>();

        private CartTableModel() {
            super(new Object[]{"Catégorie", "Prix unitaire", "Quantité", "Montant"}, 0);
        }

        private int indexOf(long categoryId) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).category().id() == categoryId) {
                    return i;
                }
            }
            return -1;
        }

        private int quantityAt(int index) {
            return entries.get(index).quantity();
        }

        private void add(TicketCategory category, int quantity) {
            entries.add(new CartEntry(category, quantity));
            addRow(rowData(category, quantity));
        }

        private void setQuantity(int index, int quantity) {
            CartEntry entry = entries.get(index);
            entries.set(index, new CartEntry(entry.category(), quantity));
            setValueAt(quantity, index, 2);
            setValueAt(money(entry.category().price().multiply(BigDecimal.valueOf(quantity))) + " Ar", index, 3);
        }

        private void removeEntry(int index) {
            entries.remove(index);
            super.removeRow(index);
        }

        private void clear() {
            entries.clear();
            setRowCount(0);
        }

        private BigDecimal total() {
            BigDecimal total = BigDecimal.ZERO;
            for (CartEntry entry : entries) {
                total = total.add(entry.category().price().multiply(BigDecimal.valueOf(entry.quantity())));
            }
            return total;
        }

        private int ticketCount() {
            return entries.stream().mapToInt(CartEntry::quantity).sum();
        }

        private List<SaleLineRequest> toRequests() {
            return entries.stream()
                    .map(entry -> new SaleLineRequest(entry.category().id(), entry.quantity()))
                    .toList();
        }

        private static Object[] rowData(TicketCategory category, int quantity) {
            return new Object[]{
                category.name(), money(category.price()) + " Ar", quantity,
                money(category.price().multiply(BigDecimal.valueOf(quantity))) + " Ar"
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        private record CartEntry(TicketCategory category, int quantity) {
        }
    }
}
