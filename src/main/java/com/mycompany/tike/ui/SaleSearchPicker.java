package com.mycompany.tike.ui;

import com.mycompany.tike.model.SaleHistoryItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class SaleSearchPicker extends JPanel {
    private final JTextField search = CatalogUiSupport.textField("");
    private final JComboBox<SaleHistoryItem> matches = new JComboBox<>();
    private final Consumer<String> onSelected;
    private List<SaleHistoryItem> sales = List.of();
    private boolean updating;

    SaleSearchPicker(Consumer<String> onSelected) {
        super(new BorderLayout(0, 5));
        this.onSelected = onSelected;
        setOpaque(false);
        setBorder(BorderFactory.createTitledBorder("Rechercher par client, événement ou vente"));
        search.setToolTipText("Rechercher par nom du client, événement ou numéro de vente");
        add(search, BorderLayout.NORTH);
        matches.setMaximumRowCount(8);
        matches.setRenderer((list, value, index, selected, focus) -> {
            String text = value == null ? "Aucun résultat" : saleLabel(value);
            JLabel label = new JLabel(text);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : Color.WHITE);
            label.setForeground(selected ? list.getSelectionForeground() : UiTheme.TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            return label;
        });
        matches.setPreferredSize(new Dimension(100, 34));
        add(matches, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateMatches();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateMatches();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateMatches();
            }
        });
        matches.addItemListener(event -> {
            if (!updating && event.getStateChange() == ItemEvent.SELECTED && event.getItem() instanceof SaleHistoryItem sale) {
                this.onSelected.accept(sale.saleNumber());
                matches.hidePopup();
            }
        });
    }

    void setSales(List<SaleHistoryItem> sales, String requiredStatus) {
        this.sales = sales.stream()
                .filter(sale -> requiredStatus == null || requiredStatus.equals(sale.status()))
                .toList();
        updateMatches();
    }

    void clearSearch() {
        search.setText("");
        matches.setSelectedIndex(-1);
    }

    @Override
    public void setToolTipText(String text) {
        search.setToolTipText(text);
        matches.setToolTipText(text);
        super.setToolTipText(text);
    }

    private void updateMatches() {
        String query = normalize(search.getText());
        List<SaleHistoryItem> filtered = query.isEmpty()
                ? List.of()
                : sales.stream().filter(sale -> matchesSearch(sale, query))
                        .limit(30).toList();
        updating = true;
        matches.removeAllItems();
        for (SaleHistoryItem sale : filtered) {
            matches.addItem(sale);
        }
        matches.setSelectedIndex(-1);
        updating = false;
        if (!filtered.isEmpty() && search.isFocusOwner()) {
            matches.showPopup();
        } else if (matches.isPopupVisible()) {
            matches.hidePopup();
        }
    }

    private static String saleLabel(SaleHistoryItem sale) {
        return sale.clientName() + " — " + sale.saleNumber() + " — " + sale.eventName();
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean matchesSearch(SaleHistoryItem sale, String query) {
        String searchable = normalize(sale.clientName() + " " + sale.saleNumber() + " " + sale.eventName());
        return Arrays.stream(query.split("\\s+")).allMatch(searchable::contains);
    }
}
