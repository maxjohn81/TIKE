package com.mycompany.tike.ui;

import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.CatalogService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

public final class EventManagementPanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ALL_STATUSES = "Tous les statuts";
    private final UserSession session;
    private final CatalogService service;
    private final boolean administrator;
    private final EventTableModel eventModel = new EventTableModel();
    private final CategoryTableModel categoryModel = new CategoryTableModel();
    private final JTable eventTable = new JTable(eventModel);
    private final JTable categoryTable = new JTable(categoryModel);
    private final JTextField search = CatalogUiSupport.textField("");
    private final JComboBox<Object> statusFilter = new JComboBox<>();

    public EventManagementPanel(UserSession session, CatalogService service) {
        this.session = session;
        this.service = service;
        this.administrator = session.getRole() == UserRole.ADMIN;
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        add(UiComponents.pageHeading("Événements", "Consultez le programme et configurez les catégories de billets."),
                BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        content.add(buildEventToolbar(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventTablePane(), categoryPanel());
        split.setResizeWeight(0.57);
        split.setBorder(null);
        split.setOpaque(false);
        content.add(split, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshCategories();
            }
        });
        refreshEvents();
    }

    private JPanel buildEventToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        search.setToolTipText("Rechercher par nom, type, lieu ou salle");
        search.addActionListener(event -> refreshEvents());
        toolbar.add(search, BorderLayout.CENTER);
        statusFilter.addItem(ALL_STATUSES);
        for (EventStatus status : EventStatus.values()) {
            statusFilter.addItem(status);
        }
        statusFilter.addActionListener(event -> refreshEvents());
        toolbar.add(statusFilter, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        buttons.setOpaque(false);
        JButton refresh = new JButton("Actualiser");
        refresh.addActionListener(event -> refreshEvents());
        buttons.add(refresh);
        if (administrator) {
            JButton add = UiComponents.primaryButton("Créer un événement");
            JButton edit = new JButton("Modifier");
            add.addActionListener(event -> editEvent(null));
            edit.addActionListener(event -> {
                Event selected = selectedEvent();
                if (selected == null) {
                    UiComponents.showResult(this, "Sélectionnez un événement à modifier.", false);
                } else {
                    editEvent(selected);
                }
            });
            buttons.add(add);
            buttons.add(edit);
        }
        toolbar.add(buttons, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel eventTablePane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.setOpaque(false);
        pane.add(UiComponents.styleTable(eventTable), BorderLayout.CENTER);
        return pane;
    }

    private JPanel categoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel label = new JLabel("Catégories de billets");
        label.setFont(UiTheme.FONT_BOLD);
        label.setForeground(UiTheme.TEXT);
        heading.add(label, BorderLayout.WEST);
        if (administrator) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
            actions.setOpaque(false);
            JButton add = UiComponents.primaryButton("Ajouter");
            JButton edit = new JButton("Modifier");
            JButton delete = new JButton("Supprimer");
            add.addActionListener(event -> editCategory(null));
            edit.addActionListener(event -> editCategory(selectedCategory()));
            delete.addActionListener(event -> deleteCategory());
            actions.add(add);
            actions.add(edit);
            actions.add(delete);
            heading.add(actions, BorderLayout.EAST);
        }
        panel.add(heading, BorderLayout.NORTH);
        panel.add(UiComponents.styleTable(categoryTable), BorderLayout.CENTER);
        return panel;
    }

    private Event selectedEvent() {
        int row = eventTable.getSelectedRow();
        return row < 0 ? null : eventModel.get(eventTable.convertRowIndexToModel(row));
    }

    private TicketCategory selectedCategory() {
        int row = categoryTable.getSelectedRow();
        return row < 0 ? null : categoryModel.get(categoryTable.convertRowIndexToModel(row));
    }

    private void refreshEvents() {
        Event selected = selectedEvent();
        try {
            EventStatus status = statusFilter.getSelectedItem() instanceof EventStatus value ? value : null;
            eventModel.setRows(service.listEvents(session, search.getText(), status));
            eventTable.clearSelection();
            if (selected != null) {
                for (int i = 0; i < eventModel.getRowCount(); i++) {
                    if (eventModel.get(i).id() == selected.id()) {
                        eventTable.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            }
            if (eventTable.getSelectedRow() < 0) {
                categoryModel.setRows(List.of());
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void refreshCategories() {
        Event event = selectedEvent();
        if (event == null) {
            categoryModel.setRows(List.of());
            return;
        }
        try {
            categoryModel.setRows(service.listCategories(session, event.id()));
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void editEvent(Event current) {
        try {
            List<Room> rooms = service.listRooms(session, "");
            JComboBox<RoomChoice> room = new JComboBox<>();
            room.addItem(new RoomChoice(null, "Aucun lieu / salle"));
            for (Room available : rooms) {
                room.addItem(new RoomChoice(available.id(), available.toString()));
            }
            if (current != null && current.roomId() != null) {
                for (int i = 0; i < room.getItemCount(); i++) {
                    if (current.roomId().equals(room.getItemAt(i).id())) {
                        room.setSelectedIndex(i);
                        break;
                    }
                }
            }
            JTextField name = CatalogUiSupport.textField(current == null ? "" : current.name());
            JTextField type = CatalogUiSupport.textField(current == null ? "" : current.type());
            DateTimePicker startsAt = new DateTimePicker(current == null ? null : current.startsAt(), false);
            DateTimePicker endsAt = new DateTimePicker(current == null ? null : current.endsAt(), true);
            DateTimePicker salesOpen = new DateTimePicker(
                    current == null ? null : current.salesOpenAt(), true);
            DateTimePicker salesClose = new DateTimePicker(
                    current == null ? null : current.salesCloseAt(), true);
            JComboBox<EventStatus> status = new JComboBox<>(EventStatus.values());
            if (current != null) {
                status.setSelectedItem(current.status());
            } else {
                status.setSelectedItem(EventStatus.BROUILLON);
            }
            javax.swing.JTextArea description = CatalogUiSupport.textArea(
                    current == null ? "" : current.description(), 3);
            JPanel form = CatalogUiSupport.form(
                    UiComponents.formRow("Nom *", name),
                    UiComponents.formRow("Type *", type),
                    UiComponents.formRow("Salle", room),
                    UiComponents.formRow("Début *", startsAt),
                    UiComponents.formRow("Fin (facultative; 1 h réservée si vide)", endsAt),
                    UiComponents.formRow("Ouverture des ventes (facultative)", salesOpen),
                    UiComponents.formRow("Fermeture des ventes (facultative)", salesClose),
                    UiComponents.formRow("Statut", status),
                    UiComponents.formRow("Description", CatalogUiSupport.scrollTextArea(description)));
            String title = current == null ? "Créer un événement" : "Modifier l'événement";
            if (!CatalogUiSupport.confirmForm(this, title, form)) {
                return;
            }
            LocalDateTime starts = startsAt.getDateTime();
            if (starts == null) {
                throw new IllegalArgumentException("Choisissez la date et l'heure de début de l'événement.");
            }
            LocalDateTime ends = endsAt.getDateTime();
            LocalDateTime open = salesOpen.getDateTime();
            LocalDateTime close = salesClose.getDateTime();
            RoomChoice selectedRoom = (RoomChoice) room.getSelectedItem();
            Long roomId = selectedRoom == null ? null : selectedRoom.id();
            Event saved = service.saveEvent(session, new Event(
                    current == null ? 0 : current.id(),
                    name.getText(), description.getText(), type.getText(), roomId, null,
                    starts, ends, open, close, (EventStatus) status.getSelectedItem()));
            UiComponents.showResult(this, "L'événement a été enregistré (ID " + saved.id() + ").", true);
            search.setText("");
            statusFilter.setSelectedItem(ALL_STATUSES);
            refreshEvents();
            for (int i = 0; i < eventModel.getRowCount(); i++) {
                if (eventModel.get(i).id() == saved.id()) {
                    eventTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void editCategory(TicketCategory current) {
        Event selected = selectedEvent();
        if (selected == null) {
            UiComponents.showResult(this, "Sélectionnez d'abord un événement.", false);
            return;
        }
        JComboBox<String> name = new JComboBox<>(new String[]{"STANDARD", "VIP", "ÉCONOMIQUE"});
        name.setEditable(true);
        if (current != null) {
            String existingName = current.name();
            boolean preset = false;
            for (int i = 0; i < name.getItemCount(); i++) {
                if (name.getItemAt(i).equalsIgnoreCase(existingName)) {
                    name.setSelectedIndex(i);
                    preset = true;
                    break;
                }
            }
            if (!preset) {
                name.setSelectedItem(existingName);
            }
        } else {
            name.setSelectedItem("STANDARD");
        }
        JTextField price = CatalogUiSupport.textField(
                current == null ? "" : current.price().toPlainString().replace('.', ','));
        JTextField quantity = CatalogUiSupport.textField(
                current == null ? "" : Integer.toString(current.totalQuantity()));
        javax.swing.JTextArea description = CatalogUiSupport.textArea(
                current == null ? "" : current.description(), 3);
        JPanel form = CatalogUiSupport.form(
                UiComponents.formRow("Catégorie *", name),
                UiComponents.formRow("Prix (ex. 25,00) *", price),
                UiComponents.formRow("Quantité totale *", quantity),
                UiComponents.formRow("Description", CatalogUiSupport.scrollTextArea(description)));
        if (!CatalogUiSupport.confirmForm(
                this, current == null ? "Ajouter une catégorie" : "Modifier la catégorie", form)) {
            return;
        }
        try {
            BigDecimal parsedPrice;
            try {
                parsedPrice = new BigDecimal(price.getText().trim().replace(',', '.'));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Saisissez un prix valide, par exemple 25,00.", invalid);
            }
            int parsedQuantity;
            try {
                parsedQuantity = Integer.parseInt(quantity.getText().trim());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("La quantité doit être un nombre entier.", invalid);
            }
            TicketCategory saved = service.saveCategory(session, new TicketCategory(
                    current == null ? 0 : current.id(), selected.id(),
                    String.valueOf(name.getEditor().getItem()),
                    parsedPrice, parsedQuantity, parsedQuantity, description.getText()));
            UiComponents.showResult(this, "La catégorie a été enregistrée (ID " + saved.id() + ").", true);
            refreshCategories();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private void deleteCategory() {
        Event selectedEvent = selectedEvent();
        TicketCategory category = selectedCategory();
        if (selectedEvent == null || category == null) {
            UiComponents.showResult(this, "Sélectionnez une catégorie à supprimer.", false);
            return;
        }
        if (!UiComponents.confirm(this, "Supprimer la catégorie « " + category.name() + " » ?")) {
            return;
        }
        try {
            service.deleteCategory(session, selectedEvent.id(), category.id());
            UiComponents.showResult(this, "La catégorie a été supprimée.", true);
            refreshCategories();
        } catch (Exception error) {
            CatalogUiSupport.showFailure(this, error);
        }
    }

    private static String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private record RoomChoice(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class EventTableModel extends DefaultTableModel {
        private List<Event> rows = new ArrayList<>();

        private EventTableModel() {
            super(new Object[]{"ID", "Événement", "Type", "Lieu / salle", "Début", "Fin", "Statut"}, 0);
        }

        private Event get(int row) {
            return rows.get(row);
        }

        private void setRows(List<Event> events) {
            rows = List.copyOf(events);
            setRowCount(0);
            for (Event event : rows) {
                addRow(new Object[]{
                    event.id(), event.name(), event.type(), event.roomName(),
                    format(event.startsAt()), format(event.endsAt()), event.status().getLabel()
                });
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static final class CategoryTableModel extends DefaultTableModel {
        private List<TicketCategory> rows = new ArrayList<>();

        private CategoryTableModel() {
            super(new Object[]{"ID", "Catégorie", "Prix", "Quantité", "Disponibles"}, 0);
        }

        private TicketCategory get(int row) {
            return rows.get(row);
        }

        private void setRows(List<TicketCategory> categories) {
            rows = List.copyOf(categories);
            setRowCount(0);
            for (TicketCategory category : rows) {
                addRow(new Object[]{
                    category.id(), category.name(), category.price().toPlainString() + " Ar",
                    category.totalQuantity(), category.availableQuantity()
                });
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
