package com.mycompany.tike.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;

public final class UiComponents {
    private UiComponents() {
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(UiTheme.FONT_BOLD);
        button.setForeground(java.awt.Color.WHITE);
        button.setBackground(UiTheme.PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JPanel pageHeading(String title, String subtitle) {
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 25));
        titleLabel.setForeground(UiTheme.TEXT);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(UiTheme.MUTED);
        heading.add(titleLabel, BorderLayout.NORTH);
        heading.add(subtitleLabel, BorderLayout.CENTER);
        return heading;
    }

    public static JPanel searchBar(String placeholder) {
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        JTextField field = new JTextField();
        field.setToolTipText(placeholder);
        field.setPreferredSize(new Dimension(260, 40));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        searchPanel.add(field, BorderLayout.CENTER);
        return searchPanel;
    }

    public static JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(0, 7));
        row.setOpaque(false);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(UiTheme.FONT_BOLD);
        fieldLabel.setForeground(UiTheme.TEXT);
        field.setPreferredSize(new Dimension(260, 40));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(0, 10, 0, 10)));
        row.add(fieldLabel, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    public static JScrollPane styleTable(JTable table) {
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setGridColor(UiTheme.BORDER);
        table.setSelectionBackground(UiTheme.PRIMARY_LIGHT);
        table.setSelectionForeground(UiTheme.TEXT);
        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));
        header.setBackground(UiTheme.SURFACE);
        header.setForeground(UiTheme.MUTED);
        header.setFont(UiTheme.FONT_BOLD);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        return scrollPane;
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(
                parent,
                message,
                "Confirmer",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static void showResult(Component parent, String message, boolean success) {
        JOptionPane.showMessageDialog(
                parent,
                message,
                success ? "Opération réussie" : "Une erreur est survenue",
                success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    public static JPanel card(JLabel title, JLabel value, JLabel description) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(UiTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        title.setForeground(UiTheme.MUTED);
        value.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 22));
        value.setForeground(UiTheme.TEXT);
        description.setForeground(UiTheme.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(description, BorderLayout.SOUTH);
        return card;
    }
}
