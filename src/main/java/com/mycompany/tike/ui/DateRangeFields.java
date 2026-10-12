package com.mycompany.tike.ui;

import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class DateRangeFields extends JPanel {
    private final JTextField from = new JTextField(10);
    private final JTextField through = new JTextField(10);

    DateRangeFields(int daysBack) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        setOpaque(false);
        LocalDate today = LocalDate.now();
        from.setText(today.minusDays(daysBack).format(DateTimeFormatter.ISO_LOCAL_DATE));
        through.setText(today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        add(new JLabel("Du"));
        add(from);
        add(new JLabel("Au"));
        add(through);
        from.setToolTipText("Date au format AAAA-MM-JJ ; vide = sans limite");
        through.setToolTipText("Date au format AAAA-MM-JJ ; vide = sans limite");
    }

    LocalDate from() {
        return parse(from.getText());
    }

    LocalDate through() {
        return parse(through.getText());
    }

    private static LocalDate parse(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? null : LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
