package com.mycompany.tike.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class DateTimePicker extends JPanel {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final JTextField display = CatalogUiSupport.textField("");
    private final boolean optional;
    private LocalDate date;
    private LocalTime time;

    DateTimePicker(LocalDateTime value, boolean optional) {
        super(new BorderLayout(6, 0));
        this.optional = optional;
        date = value == null ? null : value.toLocalDate();
        time = value == null ? LocalTime.of(19, 0) : value.toLocalTime().withSecond(0).withNano(0);
        display.setEditable(false);
        display.setFocusable(false);
        display.setToolTipText("Utilisez « Choisir » pour sélectionner la date et l'heure.");
        JButton choose = new JButton("Choisir");
        choose.addActionListener(event -> chooseDateTime());
        add(display, BorderLayout.CENTER);
        if (optional) {
            JPanel actions = new JPanel(new BorderLayout(4, 0));
            actions.add(choose, BorderLayout.CENTER);
            JButton clear = new JButton("Effacer");
            clear.addActionListener(event -> {
                date = null;
                updateDisplay();
            });
            actions.add(clear, BorderLayout.EAST);
            add(actions, BorderLayout.EAST);
        } else {
            add(choose, BorderLayout.EAST);
        }
        updateDisplay();
    }

    LocalDateTime getDateTime() {
        return date == null ? null : LocalDateTime.of(date, time);
    }

    private void chooseDateTime() {
        CalendarChooser chooser = new CalendarChooser(date == null ? LocalDate.now() : date, time);
        int result = JOptionPane.showConfirmDialog(
                this,
                chooser,
                "Choisir une date et une heure",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            date = chooser.getDate();
            time = chooser.getTime();
            updateDisplay();
        }
    }

    private void updateDisplay() {
        display.setText(date == null ? "" : DISPLAY_FORMAT.format(LocalDateTime.of(date, time)));
    }

    private static final class CalendarChooser extends JPanel {
        private static final String[] HOURS = java.util.stream.IntStream.range(0, 24)
                .mapToObj(hour -> String.format(Locale.ROOT, "%02d", hour))
                .toArray(String[]::new);
        private static final String[] MINUTES = java.util.stream.IntStream.range(0, 60)
                .mapToObj(minute -> String.format(Locale.ROOT, "%02d", minute))
                .toArray(String[]::new);

        private final JPanel calendar = new JPanel(new GridLayout(0, 7, 3, 3));
        private final JComboBox<String> hour = new JComboBox<>(HOURS);
        private final JComboBox<String> minute = new JComboBox<>(MINUTES);
        private final JLabel monthLabel = new JLabel("", JLabel.CENTER);
        private LocalDate selected;
        private YearMonth visibleMonth;

        private CalendarChooser(LocalDate selected, LocalTime time) {
            super(new BorderLayout(0, 10));
            this.selected = selected;
            visibleMonth = YearMonth.from(selected);
            hour.setSelectedItem(String.format(Locale.ROOT, "%02d", time.getHour()));
            String minuteValue = String.format(Locale.ROOT, "%02d", time.getMinute());
            minute.setSelectedItem(minuteValue);
            if (minute.getSelectedItem() == null) {
                minute.setSelectedItem("00");
            }
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            add(buildMonthHeader(), BorderLayout.NORTH);
            add(calendar, BorderLayout.CENTER);
            add(buildTimeRow(), BorderLayout.SOUTH);
            rebuildCalendar();
        }

        private JPanel buildMonthHeader() {
            JPanel header = new JPanel(new BorderLayout());
            JButton previous = new JButton("‹");
            previous.setToolTipText("Mois précédent");
            previous.addActionListener(event -> {
                visibleMonth = visibleMonth.minusMonths(1);
                rebuildCalendar();
            });
            JButton next = new JButton("›");
            next.setToolTipText("Mois suivant");
            next.addActionListener(event -> {
                visibleMonth = visibleMonth.plusMonths(1);
                rebuildCalendar();
            });
            header.add(previous, BorderLayout.WEST);
            header.add(monthLabel, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);
            return header;
        }

        private JPanel buildTimeRow() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            row.add(new JLabel("Heure :"));
            row.add(hour);
            row.add(new JLabel("h"));
            row.add(minute);
            return row;
        }

        private void rebuildCalendar() {
            monthLabel.setText(visibleMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH)
                    + " " + visibleMonth.getYear());
            calendar.removeAll();
            for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
                JLabel label = new JLabel(
                        java.time.DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.FRENCH),
                        JLabel.CENTER);
                calendar.add(label);
            }
            int firstDayOffset = visibleMonth.atDay(1).getDayOfWeek().getValue() - 1;
            for (int i = 0; i < firstDayOffset; i++) {
                calendar.add(new JLabel(""));
            }
            for (int day = 1; day <= visibleMonth.lengthOfMonth(); day++) {
                LocalDate choice = visibleMonth.atDay(day);
                JButton dateButton = new JButton(Integer.toString(day));
                dateButton.setFocusable(false);
                if (choice.equals(selected)) {
                    dateButton.setBackground(UiTheme.PRIMARY_LIGHT);
                    dateButton.setForeground(UiTheme.PRIMARY_DARK);
                }
                dateButton.addActionListener(event -> {
                    selected = choice;
                    rebuildCalendar();
                });
                calendar.add(dateButton);
            }
            calendar.revalidate();
            calendar.repaint();
        }

        private LocalDate getDate() {
            return selected;
        }

        private LocalTime getTime() {
            return LocalTime.of(Integer.parseInt((String) hour.getSelectedItem()),
                    Integer.parseInt((String) minute.getSelectedItem()));
        }
    }
}
