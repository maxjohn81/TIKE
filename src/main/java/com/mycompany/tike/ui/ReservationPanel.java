package com.mycompany.tike.ui;

import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.Reservation;
import com.mycompany.tike.model.ReservationLineRequest;
import com.mycompany.tike.model.ReservationRequest;
import com.mycompany.tike.model.ReservationStatus;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.CatalogService;
import com.mycompany.tike.service.ReservationService;
import com.mycompany.tike.service.SalesService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

public final class ReservationPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.forLanguageTag("fr-FR"));
    private static final String ALL_STATUSES = "Tous les statuts";
    private final UserSession session;
    private final CatalogService catalogService;
    private final SalesService salesService;
    private final ReservationService reservationService;
    private final JComboBox<EventChoice> events = new JComboBox<>();
    private final JComboBox<CategoryChoice> categories = new JComboBox<>();
    private final JComboBox<ClientChoice> clients = new JComboBox<>();
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField search = CatalogUiSupport.textField("");
    private final JComboBox<Object> statusFilter = new JComboBox<>();
    private final CartModel cartModel = new CartModel();
    private final JTable cartTable = new JTable(cartModel);
    private final ReservationTableModel reservationModel = new ReservationTableModel();
    private final JTable reservationTable = new JTable(reservationModel);
    private final JLabel expirationNotice = new JLabel(
            "Durée par défaut : 24 heures maximum, sans dépasser l'heure de début de l'événement.");
    private boolean loadingEvents;

    public ReservationPanel(
            UserSession session,
            CatalogService catalogService,
            SalesService salesService,
            ReservationService reservationService) {
        this.session = session;
        this.catalogService = catalogService;
        this.salesService = salesService;
        this.reservationService = reservationService;
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        add(UiComponents.pageHeading(
                "Réservations", "Réservez temporairement des billets et convertissez une réservation en vente."),
                BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        content.add(buildCreatePanel(), BorderLayout.NORTH);
        content.add(buildListPanel(), BorderLayout.CENTER);
        JScrollPane pageScroll = new JScrollPane(
                content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        pageScroll.setOpaque(false);
        pageScroll.getViewport().setOpaque(false);
        add(pageScroll, BorderLayout.CENTER);
        events.addActionListener(event -> refreshCategories());
        refreshEvents();
        refreshClients();
        refreshReservations();
    }

    private JPanel buildCreatePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 9));
        form.setOpaque(false);
        JPanel eventRow = new JPanel(new BorderLayout(8, 0));
        eventRow.setOpaque(false);
        eventRow.add(UiComponents.formRow("Événement à venir *", events), BorderLayout.CENTER);
        JButton reload = new JButton("Actualiser");
        reload.addActionListener(event -> {
            if (cartModel.getRowCount() == 0) {
                refreshEvents();
            } else {
                UiComponents.showResult(this, "Videz d'abord le panier pour changer d'événement.", false);
            }
        });
        eventRow.add(reload, BorderLayout.EAST);
        form.add(eventRow);

        JPanel categoryRow = new JPanel(new BorderLayout(8, 0));
        categoryRow.setOpaque(false);
        categoryRow.add(UiComponents.formRow("Catégorie *", categories), BorderLayout.CENTER);
        categoryRow.add(UiComponents.formRow("Quantité", quantity), BorderLayout.EAST);
        JButton add = UiComponents.primaryButton("Ajouter");
        add.addActionListener(event -> addLine());
        categoryRow.add(add, BorderLayout.SOUTH);
        form.add(categoryRow);

        JPanel clientRow = new JPanel(new BorderLayout(8, 0));
        clientRow.setOpaque(false);
        clientRow.add(UiComponents.formRow("Client *", clients), BorderLayout.CENTER);
        JButton newClient = new JButton("Nouveau client");
        newClient.addActionListener(event -> createClient());
        clientRow.add(newClient, BorderLayout.EAST);
        form.add(clientRow);
        expirationNotice.setText("Durée par défaut : " + ReservationService.DEFAULT_EXPIRATION_HOURS
                + " heures maximum, sans dépasser l'heure de début de l'événement.");
        expirationNotice.setForeground(UiTheme.MUTED);
        form.add(expirationNotice);
        panel.add(form, BorderLayout.NORTH);
        JPanel basket = new JPanel(new BorderLayout(0, 8));
        basket.setOpaque(false);
        JLabel title = new JLabel("Billets à réserver");
        title.setFont(UiTheme.FONT_BOLD);
        title.setForeground(UiTheme.TEXT);
        basket.add(title, BorderLayout.NORTH);
        JScrollPane cartScroll = UiComponents.styleTable(cartTable);
        cartScroll.setPreferredSize(new java.awt.Dimension(600, 90));
        basket.add(cartScroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton remove = new JButton("Retirer");
        remove.addActionListener(event -> removeLine());
        JButton clear = new JButton("Vider");
        clear.addActionListener(event -> clearCart());
        JButton create = UiComponents.primaryButton("Créer la réservation");
        create.addActionListener(event -> createReservation());
        actions.add(remove);
        actions.add(clear);
        actions.add(create);
        basket.add(actions, BorderLayout.SOUTH);
        panel.add(basket, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        search.setToolTipText("Rechercher par numéro, événement ou client");
        search.addActionListener(event -> refreshReservations());
        toolbar.add(search, BorderLayout.CENTER);
        statusFilter.addItem(ALL_STATUSES);
        for (ReservationStatus status : ReservationStatus.values()) {
            statusFilter.addItem(status);
        }
        statusFilter.addActionListener(event -> refreshReservations());
        toolbar.add(statusFilter, BorderLayout.WEST);
        JButton refresh = new JButton("Actualiser");
        refresh.addActionListener(event -> refreshReservations());
        toolbar.add(refresh, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);
        JButton cancel = new JButton("Annuler");
        cancel.addActionListener(event -> cancelSelected());
        JButton convert = UiComponents.primaryButton("Convertir en vente");
        convert.addActionListener(event -> convertSelected());
        actionButtons.add(cancel);
        actionButtons.add(convert);
        actions.add(actionButtons, BorderLayout.EAST);
        panel.add(UiComponents.styleTable(reservationTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshEvents() {
        if (!events.isEnabled() && cartModel.getRowCount() > 0) {
            return;
        }
        try {
            loadingEvents = true;
            events.removeAllItems();
            LocalDateTime now = LocalDateTime.now();
            List<Event> found = catalogService.listEvents(session, "", null).stream()
                    .filter(event -> event.startsAt() != null && event.startsAt().isAfter(now))
                    .filter(event -> event.status() == EventStatus.PROGRAMME || event.status() == EventStatus.EN_VENTE)
                    .toList();
            for (Event event : found) {
                events.addItem(new EventChoice(event));
            }
            if (found.isEmpty()) {
                events.addItem(new EventChoice(null));
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        } finally {
            loadingEvents = false;
        }
        refreshCategories();
    }

    private void refreshCategories() {
        if (loadingEvents) {
            return;
        }
        EventChoice selected = (EventChoice) events.getSelectedItem();
        categories.removeAllItems();
        try {
            if (selected == null || selected.event() == null) {
                categories.addItem(new CategoryChoice(null));
                return;
            }
            List<TicketCategory> found = catalogService.listCategories(session, selected.event().id());
            for (TicketCategory category : found) {
                if (category.availableQuantity() > 0) {
                    categories.addItem(new CategoryChoice(category));
                }
            }
            if (categories.getItemCount() == 0) {
                categories.addItem(new CategoryChoice(null));
            }
            categories.setRenderer((list, value, index, isSelected, focus) -> {
                JLabel label = new JLabel(value == null ? "" : value.display());
                label.setOpaque(true);
                label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
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

    private void refreshReservations() {
        try {
            ReservationStatus status = statusFilter.getSelectedItem() instanceof ReservationStatus value ? value : null;
            reservationModel.setRows(reservationService.list(session, search.getText(), status));
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void addLine() {
        CategoryChoice choice = (CategoryChoice) categories.getSelectedItem();
        if (choice == null || choice.category() == null) {
            UiComponents.showResult(this, "Sélectionnez une catégorie ayant du stock disponible.", false);
            return;
        }
        TicketCategory category = choice.category();
        int index = cartModel.find(category.id());
        int requested = (Integer) quantity.getValue();
        int alreadyInCart = index < 0 ? 0 : cartModel.quantityAt(index);
        if ((long) alreadyInCart + requested > category.availableQuantity()) {
            UiComponents.showResult(this, "La quantité dépasse le stock disponible ("
                    + category.availableQuantity() + ").", false);
            return;
        }
        if (index < 0) {
            cartModel.add(category, requested);
        } else {
            cartModel.setQuantity(index, alreadyInCart + requested);
        }
        events.setEnabled(false);
    }

    private void removeLine() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            UiComponents.showResult(this, "Sélectionnez une ligne à retirer.", false);
            return;
        }
        cartModel.removeEntry(cartTable.convertRowIndexToModel(row));
        if (cartModel.getRowCount() == 0) {
            events.setEnabled(true);
        }
    }

    private void clearCart() {
        cartModel.clear();
        events.setEnabled(true);
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
            Client saved = salesService.saveClient(
                    session, new Client(0, name.getText(), phone.getText(), email.getText()));
            refreshClients();
            for (int i = 0; i < clients.getItemCount(); i++) {
                ClientChoice choice = clients.getItemAt(i);
                if (choice.client() != null && choice.client().id() == saved.id()) {
                    clients.setSelectedIndex(i);
                    break;
                }
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void createReservation() {
        EventChoice selectedEvent = (EventChoice) events.getSelectedItem();
        ClientChoice selectedClient = (ClientChoice) clients.getSelectedItem();
        if (selectedEvent == null || selectedEvent.event() == null) {
            UiComponents.showResult(this, "Sélectionnez un événement à venir.", false);
            return;
        }
        if (selectedClient == null || selectedClient.client() == null) {
            UiComponents.showResult(this, "Sélectionnez ou créez un client.", false);
            return;
        }
        if (cartModel.getRowCount() == 0) {
            UiComponents.showResult(this, "Ajoutez au moins une catégorie à la réservation.", false);
            return;
        }
        String confirmation = "<html>Client : <b>" + selectedClient.client().fullName() + "</b><br>"
                + "Événement : <b>" + selectedEvent.event().name() + "</b><br>"
                + "Billets : <b>" + cartModel.ticketCount() + "</b><br>"
                + "Montant indicatif : <b>" + money(cartModel.total()) + " Ar</b><br>"
                + "Expiration : dans 24 h maximum (ou avant si l'événement commence).<br><br>"
                + "Créer cette réservation temporaire ?</html>";
        if (!UiComponents.confirm(this, confirmation)) {
            return;
        }
        try {
            var saved = reservationService.create(session, new ReservationRequest(
                    selectedEvent.event().id(), selectedClient.client().id(), cartModel.toRequests()));
            UiComponents.showResult(this,
                    "Réservation créée : " + saved.number()
                            + "\nExpiration : " + DATE_FORMAT.format(saved.expiresAt()), true);
            cartModel.clear();
            events.setEnabled(true);
            refreshEvents();
            refreshReservations();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
            refreshEvents();
        }
    }

    private Reservation selectedReservation() {
        int row = reservationTable.getSelectedRow();
        return row < 0 ? null : reservationModel.get(reservationTable.convertRowIndexToModel(row));
    }

    private void cancelSelected() {
        Reservation selected = selectedReservation();
        if (selected == null) {
            UiComponents.showResult(this, "Sélectionnez une réservation.", false);
            return;
        }
        if (selected.status() != ReservationStatus.EN_ATTENTE) {
            UiComponents.showResult(this, "Seule une réservation en attente peut être annulée.", false);
            return;
        }
        if (!UiComponents.confirm(this, "Annuler la réservation « " + selected.number() + " » ?")) {
            return;
        }
        try {
            reservationService.cancel(session, selected.id());
            UiComponents.showResult(this, "Réservation annulée ; le stock est de nouveau disponible.", true);
            refreshReservations();
            refreshEvents();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void convertSelected() {
        Reservation selected = selectedReservation();
        if (selected == null) {
            UiComponents.showResult(this, "Sélectionnez une réservation.", false);
            return;
        }
        if (selected.status() != ReservationStatus.EN_ATTENTE) {
            UiComponents.showResult(this, "Seule une réservation en attente peut être convertie.", false);
            return;
        }
        JComboBox<PaymentMethod> method = new JComboBox<>(PaymentMethod.values());
        JTextField reference = CatalogUiSupport.textField("");
        JPanel form = CatalogUiSupport.form(
                UiComponents.formRow("Mode de paiement *", method),
                UiComponents.formRow("Référence (facultative)", reference));
        if (!CatalogUiSupport.confirmForm(this, "Convertir la réservation en vente", form)) {
            return;
        }
        if (selected.total().signum() > 0
                && !UiComponents.confirm(this, "Vérifiez que le paiement de "
                        + money(selected.total()) + " Ar a bien été reçu. Continuer ?")) {
            return;
        }
        try {
            SaleResult sale = reservationService.convert(
                    session, selected.id(), (PaymentMethod) method.getSelectedItem(), reference.getText());
            UiComponents.showResult(this,
                    "Réservation convertie en vente.\nNuméro de vente : " + sale.number()
                            + "\nBillets générés : " + sale.ticketCount(), true);
            refreshReservations();
            refreshEvents();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
            refreshReservations();
        }
    }

    private static String money(BigDecimal value) {
        return MONEY.format(value);
    }

    private record EventChoice(Event event) {
        @Override
        public String toString() {
            return event == null ? "Aucun événement disponible"
                    : event.name() + " — " + DATE_FORMAT.format(event.startsAt());
        }
    }

    private record CategoryChoice(TicketCategory category) {
        private String display() {
            return category == null ? "Aucune catégorie disponible"
                    : category.name() + " — " + money(category.price()) + " Ar — "
                            + category.availableQuantity() + " disponible(s)";
        }
    }

    private record ClientChoice(Client client) {
        @Override
        public String toString() {
            return client == null ? "Sélectionnez un client" : client.toString();
        }
    }

    private static final class CartModel extends DefaultTableModel {
        private final List<CartEntry> entries = new ArrayList<>();

        private CartModel() {
            super(new Object[]{"Catégorie", "Prix unitaire", "Quantité", "Montant indicatif"}, 0);
        }

        private int find(long id) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).category().id() == id) {
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
            addRow(row(category, quantity));
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

        private int ticketCount() {
            return entries.stream().mapToInt(CartEntry::quantity).sum();
        }

        private BigDecimal total() {
            return entries.stream()
                    .map(entry -> entry.category().price().multiply(BigDecimal.valueOf(entry.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private List<ReservationLineRequest> toRequests() {
            return entries.stream()
                    .map(entry -> new ReservationLineRequest(entry.category().id(), entry.quantity()))
                    .toList();
        }

        private static Object[] row(TicketCategory category, int quantity) {
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

    private static final class ReservationTableModel extends DefaultTableModel {
        private List<Reservation> rows = List.of();

        private ReservationTableModel() {
            super(new Object[]{"ID", "N° réservation", "Événement", "Client", "Créée le", "Expire le",
                "Statut", "Billets", "Montant indicatif"}, 0);
        }

        private void setRows(List<Reservation> reservations) {
            rows = List.copyOf(reservations);
            setRowCount(0);
            for (Reservation reservation : rows) {
                int tickets = reservation.lines().stream().mapToInt(line -> line.quantity()).sum();
                addRow(new Object[]{
                    reservation.id(),
                    reservation.number(),
                    reservation.eventName(),
                    reservation.clientName(),
                    DATE_FORMAT.format(reservation.createdAt()),
                    DATE_FORMAT.format(reservation.expiresAt()),
                    reservation.status().getLabel(),
                    tickets,
                    money(reservation.total()) + " Ar"
                });
            }
        }

        private Reservation get(int row) {
            return rows.get(row);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
