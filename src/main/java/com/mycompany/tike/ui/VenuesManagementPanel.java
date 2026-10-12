package com.mycompany.tike.ui;

import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.Venue;
import com.mycompany.tike.service.CatalogService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public final class VenuesManagementPanel extends JPanel {
    private final UserSession session;
    private final CatalogService service;
    private final VenueTab venueTab = new VenueTab();
    private final RoomTab roomTab = new RoomTab();

    public VenuesManagementPanel(UserSession session, CatalogService service) {
        this.session = session;
        this.service = service;
        setOpaque(false);
        setLayout(new BorderLayout());
        add(UiComponents.pageHeading("Lieux & salles", "Gérez les établissements et leurs capacités."),
                BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Lieux", venueTab);
        tabs.addTab("Salles", roomTab);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        body.add(tabs, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        venueTab.refresh();
        roomTab.refresh();
    }

    private final class VenueTab extends JPanel {
        private final VenueTableModel model = new VenueTableModel();
        private final JTable table = new JTable(model);
        private final JTextField search = CatalogUiSupport.textField("");

        private VenueTab() {
            super(new BorderLayout(0, 12));
            setOpaque(false);
            JPanel toolbar = new JPanel(new BorderLayout(10, 0));
            toolbar.setOpaque(false);
            search.setToolTipText("Rechercher un lieu ou une ville");
            search.addActionListener(event -> refresh());
            toolbar.add(search, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            JButton find = new JButton("Rechercher");
            JButton add = UiComponents.primaryButton("Ajouter un lieu");
            JButton edit = new JButton("Modifier");
            JButton delete = new JButton("Supprimer");
            find.addActionListener(event -> refresh());
            add.addActionListener(event -> editVenue(null));
            edit.addActionListener(event -> {
                Venue venue = selected();
                if (venue == null) {
                    UiComponents.showResult(this, "Sélectionnez un lieu à modifier.", false);
                } else {
                    editVenue(venue);
                }
            });
            delete.addActionListener(event -> deleteVenue());
            buttons.add(find);
            buttons.add(add);
            buttons.add(edit);
            buttons.add(delete);
            toolbar.add(buttons, BorderLayout.EAST);
            add(toolbar, BorderLayout.NORTH);
            add(UiComponents.styleTable(table), BorderLayout.CENTER);
        }

        private Venue selected() {
            int row = table.getSelectedRow();
            return row < 0 ? null : model.get(table.convertRowIndexToModel(row));
        }

        private void refresh() {
            try {
                model.setRows(service.listVenues(session, search.getText()));
                table.clearSelection();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }

        private void editVenue(Venue current) {
            JTextField name = CatalogUiSupport.textField(current == null ? "" : current.name());
            JTextField address = CatalogUiSupport.textField(current == null ? "" : current.address());
            JTextField city = CatalogUiSupport.textField(current == null ? "" : current.city());
            javax.swing.JTextArea description = CatalogUiSupport.textArea(
                    current == null ? "" : current.description(), 3);
            JPanel form = CatalogUiSupport.form(
                    UiComponents.formRow("Nom *", name),
                    UiComponents.formRow("Adresse", address),
                    UiComponents.formRow("Ville", city),
                    UiComponents.formRow("Description", CatalogUiSupport.scrollTextArea(description)));
            if (!CatalogUiSupport.confirmForm(this, current == null ? "Ajouter un lieu" : "Modifier le lieu", form)) {
                return;
            }
            try {
                service.saveVenue(session, new Venue(current == null ? 0 : current.id(),
                        name.getText(), address.getText(), city.getText(), description.getText()));
                UiComponents.showResult(this, "Le lieu a été enregistré.", true);
                refresh();
                roomTab.refresh();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }

        private void deleteVenue() {
            Venue venue = selected();
            if (venue == null) {
                UiComponents.showResult(this, "Sélectionnez un lieu.", false);
                return;
            }
            if (!UiComponents.confirm(this, "Supprimer le lieu « " + venue.name() + " » ?")) {
                return;
            }
            try {
                service.deleteVenue(session, venue.id());
                UiComponents.showResult(this, "Le lieu a été supprimé.", true);
                refresh();
                roomTab.refresh();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }
    }

    private final class RoomTab extends JPanel {
        private final RoomTableModel model = new RoomTableModel();
        private final JTable table = new JTable(model);
        private final JTextField search = CatalogUiSupport.textField("");

        private RoomTab() {
            super(new BorderLayout(0, 12));
            setOpaque(false);
            JPanel toolbar = new JPanel(new BorderLayout(10, 0));
            toolbar.setOpaque(false);
            search.setToolTipText("Rechercher une salle ou un lieu");
            search.addActionListener(event -> refresh());
            toolbar.add(search, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            JButton find = new JButton("Rechercher");
            JButton add = UiComponents.primaryButton("Ajouter une salle");
            JButton edit = new JButton("Modifier");
            JButton delete = new JButton("Supprimer");
            find.addActionListener(event -> refresh());
            add.addActionListener(event -> editRoom(null));
            edit.addActionListener(event -> {
                Room room = selected();
                if (room == null) {
                    UiComponents.showResult(this, "Sélectionnez une salle à modifier.", false);
                } else {
                    editRoom(room);
                }
            });
            delete.addActionListener(event -> deleteRoom());
            buttons.add(find);
            buttons.add(add);
            buttons.add(edit);
            buttons.add(delete);
            toolbar.add(buttons, BorderLayout.EAST);
            add(toolbar, BorderLayout.NORTH);
            add(UiComponents.styleTable(table), BorderLayout.CENTER);
        }

        private Room selected() {
            int row = table.getSelectedRow();
            return row < 0 ? null : model.get(table.convertRowIndexToModel(row));
        }

        private void refresh() {
            try {
                model.setRows(service.listRooms(session, search.getText()));
                table.clearSelection();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }

        private void editRoom(Room current) {
            try {
                List<Venue> venues = service.listVenues(session, "");
                if (venues.isEmpty()) {
                    UiComponents.showResult(this, "Créez d'abord un lieu avant d'ajouter une salle.", false);
                    return;
                }
                JComboBox<Venue> venue = new JComboBox<>(venues.toArray(Venue[]::new));
                venue.setRenderer((list, value, index, selected, focus) -> {
                    JLabel label = new JLabel(value == null ? "" : value.name());
                    label.setOpaque(true);
                    label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                    label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                    return label;
                });
                if (current != null) {
                    for (int i = 0; i < venue.getItemCount(); i++) {
                        if (venue.getItemAt(i).id() == current.venueId()) {
                            venue.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                JTextField name = CatalogUiSupport.textField(current == null ? "" : current.name());
                JTextField capacity = CatalogUiSupport.textField(current == null ? "" : Integer.toString(current.capacity()));
                JPanel form = CatalogUiSupport.form(
                        UiComponents.formRow("Lieu *", venue),
                        UiComponents.formRow("Nom de la salle *", name),
                        UiComponents.formRow("Capacité *", capacity));
                if (!CatalogUiSupport.confirmForm(
                        this, current == null ? "Ajouter une salle" : "Modifier la salle", form)) {
                    return;
                }
                int parsedCapacity;
                try {
                    parsedCapacity = Integer.parseInt(capacity.getText().trim());
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("La capacité doit être un nombre entier.", invalid);
                }
                Venue selectedVenue = (Venue) venue.getSelectedItem();
                service.saveRoom(session, new Room(current == null ? 0 : current.id(),
                        selectedVenue.id(), selectedVenue.name(), name.getText(), parsedCapacity));
                UiComponents.showResult(this, "La salle a été enregistrée.", true);
                refresh();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }

        private void deleteRoom() {
            Room room = selected();
            if (room == null) {
                UiComponents.showResult(this, "Sélectionnez une salle.", false);
                return;
            }
            if (!UiComponents.confirm(this, "Supprimer la salle « " + room.name() + " » ?")) {
                return;
            }
            try {
                service.deleteRoom(session, room.id());
                UiComponents.showResult(this, "La salle a été supprimée.", true);
                refresh();
            } catch (Exception error) {
                CatalogUiSupport.showFailure(this, error);
            }
        }
    }

    private static final class VenueTableModel extends DefaultTableModel {
        private List<Venue> rows = new ArrayList<>();

        private VenueTableModel() {
            super(new Object[]{"ID", "Nom", "Adresse", "Ville"}, 0);
        }

        private Venue get(int row) {
            return rows.get(row);
        }

        private void setRows(List<Venue> venues) {
            rows = List.copyOf(venues);
            setRowCount(0);
            for (Venue venue : rows) {
                addRow(new Object[]{venue.id(), venue.name(), venue.address(), venue.city()});
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static final class RoomTableModel extends DefaultTableModel {
        private List<Room> rows = new ArrayList<>();

        private RoomTableModel() {
            super(new Object[]{"ID", "Lieu", "Salle", "Capacité"}, 0);
        }

        private Room get(int row) {
            return rows.get(row);
        }

        private void setRows(List<Room> rooms) {
            rows = List.copyOf(rooms);
            setRowCount(0);
            for (Room room : rows) {
                addRow(new Object[]{room.id(), room.venueName(), room.name(), room.capacity()});
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
