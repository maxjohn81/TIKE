package com.mycompany.tike.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

final class CatalogUiSupport {
    private CatalogUiSupport() {
    }

    static JTextField textField(String value) {
        JTextField field = new JTextField(value == null ? "" : value);
        field.setPreferredSize(new Dimension(280, 38));
        return field;
    }

    static JTextArea textArea(String value, int rows) {
        JTextArea area = new JTextArea(value == null ? "" : value, rows, 28);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return area;
    }

    static JScrollPane scrollTextArea(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(360, 82));
        return scroll;
    }

    static JPanel form(JComponent... rows) {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 0, 10));
        form.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (JComponent row : rows) {
            form.add(row);
        }
        return form;
    }

    static boolean confirmForm(Component parent, String title, JPanel form) {
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(470, Math.min(520, Math.max(240, form.getPreferredSize().height))));
        return JOptionPane.showConfirmDialog(
                parent, scroll, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    static void showFailure(Component parent, Exception error) {
        UiComponents.showResult(parent, describe(error), false);
    }

    private static String describe(Exception error) {
        if (error instanceof IllegalArgumentException || error instanceof SecurityException) {
            return error.getMessage();
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return switch (sqlException.getSQLState() == null ? "" : sqlException.getSQLState()) {
                    case "23505" -> "Une entrée avec ces informations existe déjà.";
                    case "23503" -> "Suppression impossible : cet élément est encore utilisé.";
                    case "40001" -> "Une modification concurrente a été détectée. Actualisez puis réessayez.";
                    default -> "Erreur de base de données : " + sqlException.getMessage();
                };
            }
        }
        return error.getMessage() == null ? "Une erreur inattendue est survenue." : error.getMessage();
    }
}
