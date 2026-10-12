package com.mycompany.tike.ui;

import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.service.ReportingService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public final class ActivityHistoryPanel extends JPanel {
    private final UserSession session;
    private final ReportingService service;
    private final DateRangeFields dates = new DateRangeFields(30);
    private final JTextField search = new JTextField(18);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Date", "Utilisateur", "Action", "Type", "ID objet", "Détails"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final javax.swing.JLabel status = new javax.swing.JLabel(" ");

    public ActivityHistoryPanel(UserSession session, ReportingService service) {
        super(new BorderLayout(0, 14));
        this.session = session;
        this.service = service;
        setOpaque(false);
        add(UiComponents.pageHeading("Historique des activités", "Journal des opérations métier et contrôles d'accès."),
                BorderLayout.NORTH);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filters.setOpaque(false);
        filters.add(dates);
        search.setToolTipText("Filtrer par action ou détail");
        filters.add(search);
        JButton refresh = UiComponents.primaryButton("Filtrer");
        refresh.addActionListener(event -> refresh());
        filters.add(refresh);
        JButton export = UiComponents.primaryButton("Exporter CSV");
        export.addActionListener(event -> export());
        filters.add(export);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(filters, BorderLayout.NORTH);
        content.add(UiComponents.styleTable(new javax.swing.JTable(model)), BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        try {
            List<ReportingDao.ActivityEntry> entries =
                    service.activity(session, dates.from(), dates.through(), search.getText());
            model.setRowCount(0);
            for (ReportingDao.ActivityEntry entry : entries) {
                model.addRow(new Object[] {
                    entry.createdAt().toString().replace('T', ' '), entry.user(), entry.action(),
                    entry.objectType(), entry.objectId() == null ? "" : entry.objectId(), entry.details()
                });
            }
            status.setForeground(UiTheme.MUTED);
            status.setText(entries.size() == 500
                    ? "500 entrées maximum affichées (les plus récentes)."
                    : entries.size() + " entrée(s) affichée(s).");
        } catch (Exception error) {
            status.setForeground(UiTheme.ERROR);
            status.setText("Erreur lors du chargement de l'historique : " + error.getMessage());
        }
    }

    private void export() {
        List<List<String>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < model.getRowCount(); rowIndex++) {
            List<String> row = new ArrayList<>();
            for (int column = 0; column < model.getColumnCount(); column++) {
                row.add(String.valueOf(model.getValueAt(rowIndex, column)));
            }
            rows.add(row);
        }
        ReportsPanel.saveCsv(
                List.of("Date", "Utilisateur", "Action", "Type", "ID objet", "Details"), rows);
    }
}
