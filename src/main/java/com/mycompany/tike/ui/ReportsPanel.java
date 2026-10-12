package com.mycompany.tike.ui;

import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.ReportingService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public final class ReportsPanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final UserSession session;
    private final ReportingService service;
    private final DateRangeFields dates = new DateRangeFields(30);
    private final JComboBox<ReportingDao.EventOption> events = new JComboBox<>();
    private final DefaultTableModel salesModel = tableModel(
            "N° vente", "Événement", "Date", "Client", "Montant", "Statut", "Billets");
    private final DefaultTableModel fillModel = tableModel(
            "Événement", "Date", "Capacité", "Billets vendus", "Remplissage");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel(" ");
    private String eventLoadError;

    public ReportsPanel(UserSession session, ReportingService service) {
        super(new BorderLayout(0, 14));
        this.session = session;
        this.service = service;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        add(UiComponents.pageHeading("Rapports", "Ventes et taux de remplissage par période et événement."),
                BorderLayout.NORTH);

        events.addItem(new ReportingDao.EventOption(null, "Tous les événements"));
        try {
            service.events(session).forEach(events::addItem);
        } catch (Exception error) {
            eventLoadError = error.getMessage();
            status.setForeground(UiTheme.ERROR);
        }
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filters.setOpaque(false);
        filters.add(dates);
        filters.add(events);
        filters.add(UiComponents.primaryButton("Actualiser"));
        javax.swing.JButton refresh = (javax.swing.JButton) filters.getComponent(filters.getComponentCount() - 1);
        refresh.addActionListener(event -> refresh());
        javax.swing.JButton export = UiComponents.primaryButton("Exporter CSV");
        filters.add(export);
        export.addActionListener(event -> exportCurrentTab());
        filters.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));

        JTable salesTable = new JTable(salesModel);
        JTable fillTable = new JTable(fillModel);
        tabs.addTab("Ventes", UiComponents.styleTable(salesTable));
        tabs.addTab("Remplissage des événements", UiComponents.styleTable(fillTable));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(filters, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        refresh();
    }

    private void refresh() {
        try {
            var from = dates.from();
            var through = dates.through();
            if (from != null && through != null && from.isAfter(through)) {
                throw new IllegalArgumentException("La date de début doit précéder la date de fin.");
            }
            ReportingDao.EventOption selected = (ReportingDao.EventOption) events.getSelectedItem();
            Long eventId = selected == null ? null : selected.id();

            List<ReportingDao.SalesReportRow> sales = service.sales(session, from, through, eventId);
            List<ReportingDao.EventFillRow> fill = service.eventFill(session, from, through, eventId);
            salesModel.setRowCount(0);
            for (ReportingDao.SalesReportRow row : sales) {
                salesModel.addRow(new Object[] {
                    row.saleNumber(), row.eventName(), row.soldAt().format(DATE_TIME), row.customer(),
                    row.amount().toPlainString(), row.status(), row.ticketCount()
                });
            }
            fillModel.setRowCount(0);
            for (ReportingDao.EventFillRow row : fill) {
                fillModel.addRow(new Object[] {
                    row.eventName(), row.startsAt().format(DATE_TIME), row.capacity(), row.sold(),
                    String.format(Locale.ROOT, "%.1f %%", row.fillRate())
                });
            }
            if (eventLoadError == null) {
                status.setForeground(UiTheme.MUTED);
                status.setText(sales.size() + " vente(s) · " + fill.size() + " événement(s)");
            } else {
                status.setForeground(UiTheme.ERROR);
                status.setText("Filtre événement indisponible : " + eventLoadError);
            }
        } catch (Exception error) {
            status.setForeground(UiTheme.ERROR);
            status.setText("Erreur lors du chargement des rapports : " + error.getMessage());
        }
    }

    private void exportCurrentTab() {
        boolean salesSelected = tabs.getSelectedIndex() == 0;
        List<String> headers = salesSelected
                ? List.of("Numero vente", "Evenement", "Date", "Client", "Montant", "Statut", "Billets")
                : List.of("Evenement", "Date", "Capacite", "Billets vendus", "Remplissage");
        List<List<String>> rows = new ArrayList<>();
        DefaultTableModel model = salesSelected ? salesModel : fillModel;
        for (int rowIndex = 0; rowIndex < model.getRowCount(); rowIndex++) {
            List<String> row = new ArrayList<>();
            for (int column = 0; column < model.getColumnCount(); column++) {
                row.add(String.valueOf(model.getValueAt(rowIndex, column)));
            }
            rows.add(row);
        }
        saveCsv(headers, rows);
    }

    static void saveCsv(List<String> headers, List<List<String>> rows) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exporter le rapport CSV");
        chooser.setSelectedFile(new java.io.File("rapport-tike.csv"));
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            CsvExporter.write(path, headers, rows);
            JOptionPane.showMessageDialog(null, "Rapport exporté : " + path,
                    "Export terminé", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(null, "Impossible d'exporter le CSV : " + error.getMessage(),
                    "Erreur d'export", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
