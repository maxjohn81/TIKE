package com.mycompany.tike.ui;

import com.mycompany.tike.dao.ReportingDao;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

final class DashboardCharts {
    private static final Color[] PALETTE = {
        UiTheme.PRIMARY, new Color(0x22A699), new Color(0xF4A261),
        new Color(0xE76F51), new Color(0x5975C5), new Color(0xA66DD4)
    };

    private DashboardCharts() {
    }

    static JPanel card(String title, JPanel chart) {
        JPanel card = new JPanel(new java.awt.BorderLayout(0, 8));
        card.setBackground(UiTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));
        javax.swing.JLabel heading = new javax.swing.JLabel(title);
        heading.setFont(UiTheme.FONT_BOLD);
        heading.setForeground(UiTheme.TEXT);
        card.add(heading, java.awt.BorderLayout.NORTH);
        card.add(chart, java.awt.BorderLayout.CENTER);
        return card;
    }

    static final class BarChart extends JPanel {
        private List<ReportingDao.ChartValue> values = List.of();
        private String emptyMessage;

        BarChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 270));
            setMinimumSize(new Dimension(0, 220));
        }

        void setValues(List<ReportingDao.ChartValue> values) {
            this.values = List.copyOf(values);
            emptyMessage = null;
            repaint();
        }

        void setError(String message) {
            values = List.of();
            emptyMessage = message;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 58;
            int right = getWidth() - 14;
            int top = 18;
            int bottom = getHeight() - 43;
            if (values.isEmpty()) {
                drawMessage(g, emptyMessage == null ? "Aucune vente payée sur la période." : emptyMessage);
                g.dispose();
                return;
            }

            BigDecimal max = values.stream().map(ReportingDao.ChartValue::value)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            double scaleMax = max.signum() == 0 ? 1 : niceScale(max.doubleValue());
            g.setFont(UiTheme.FONT);
            FontMetrics metrics = g.getFontMetrics();
            for (int line = 0; line <= 4; line++) {
                int y = bottom - (bottom - top) * line / 4;
                g.setColor(UiTheme.BORDER);
                g.setStroke(new BasicStroke(1f));
                g.drawLine(left, y, right, y);
                String label = compactAmount(scaleMax * line / 4);
                g.setColor(UiTheme.MUTED);
                g.drawString(label, Math.max(0, left - metrics.stringWidth(label) - 8), y + 4);
            }

            int plotWidth = Math.max(1, right - left);
            int slotWidth = plotWidth / values.size();
            int barWidth = Math.max(8, Math.min(46, slotWidth * 58 / 100));
            for (int index = 0; index < values.size(); index++) {
                ReportingDao.ChartValue value = values.get(index);
                int height = (int) Math.round((bottom - top) * value.value().doubleValue() / scaleMax);
                int x = left + slotWidth * index + (slotWidth - barWidth) / 2;
                int y = bottom - height;
                g.setColor(PALETTE[index % PALETTE.length]);
                g.fillRoundRect(x, y, barWidth, Math.max(2, height), 8, 8);
                String month = value.label();
                int textWidth = metrics.stringWidth(month);
                g.setColor(UiTheme.MUTED);
                g.drawString(month, x + barWidth / 2 - textWidth / 2, bottom + 22);
            }
            g.dispose();
        }

        private static double niceScale(double max) {
            double step = Math.pow(10, Math.floor(Math.log10(max)));
            double normalized = max / step;
            double rounded = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
            return rounded * step;
        }

        private static String compactAmount(double amount) {
            if (amount >= 1_000_000_000) {
                return String.format(Locale.FRANCE, "%.1f Md", amount / 1_000_000_000);
            }
            if (amount >= 1_000_000) {
                return String.format(Locale.FRANCE, "%.1f M", amount / 1_000_000);
            }
            if (amount >= 1_000) {
                return String.format(Locale.FRANCE, "%.0f k", amount / 1_000);
            }
            return NumberFormat.getIntegerInstance(Locale.FRANCE).format(Math.round(amount));
        }

        private void drawMessage(Graphics2D g, String message) {
            g.setColor(UiTheme.MUTED);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(message, Math.max(6, (getWidth() - metrics.stringWidth(message)) / 2), getHeight() / 2);
        }
    }

    static final class PieChart extends JPanel {
        private List<ReportingDao.ChartValue> values = List.of();
        private String emptyMessage;

        PieChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 270));
            setMinimumSize(new Dimension(0, 220));
        }

        void setValues(List<ReportingDao.ChartValue> values) {
            this.values = List.copyOf(values);
            emptyMessage = null;
            repaint();
        }

        void setError(String message) {
            values = List.of();
            emptyMessage = message;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (values.isEmpty()) {
                g.setColor(UiTheme.MUTED);
                String message = emptyMessage == null ? "Aucune vente de billet à répartir." : emptyMessage;
                g.drawString(message, Math.max(6, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2),
                        getHeight() / 2);
                g.dispose();
                return;
            }

            double total = values.stream().mapToDouble(item -> item.value().doubleValue()).sum();
            int diameter = Math.max(120, Math.min(getHeight() - 24, getWidth() / 2 - 18));
            int x = 12;
            int y = (getHeight() - diameter) / 2;
            double start = 90;
            for (int index = 0; index < values.size(); index++) {
                double extent = values.get(index).value().doubleValue() / total * 360;
                g.setColor(PALETTE[index % PALETTE.length]);
                g.fill(new Arc2D.Double(x, y, diameter, diameter, start, extent, Arc2D.PIE));
                start += extent;
            }

            int legendX = x + diameter + 18;
            int legendY = Math.max(18, (getHeight() - values.size() * 29) / 2);
            g.setFont(UiTheme.FONT);
            FontMetrics metrics = g.getFontMetrics();
            for (int index = 0; index < values.size(); index++) {
                ReportingDao.ChartValue value = values.get(index);
                int rowY = legendY + index * 29;
                g.setColor(PALETTE[index % PALETTE.length]);
                g.fillRoundRect(legendX, rowY, 12, 12, 4, 4);
                double percent = value.value().doubleValue() / total * 100;
                String text = value.label() + " · " + NumberFormat.getIntegerInstance(Locale.FRANCE)
                        .format(value.value().longValue()) + " (" + String.format(Locale.FRANCE, "%.0f", percent) + "%)";
                g.setColor(UiTheme.TEXT);
                g.drawString(clip(text, metrics, getWidth() - legendX - 8), legendX + 19, rowY + 11);
            }
            g.dispose();
        }

        private static String clip(String text, FontMetrics metrics, int width) {
            if (metrics.stringWidth(text) <= width) {
                return text;
            }
            String shortened = text;
            while (!shortened.isEmpty() && metrics.stringWidth(shortened + "…") > width) {
                shortened = shortened.substring(0, shortened.length() - 1);
            }
            return shortened + "…";
        }
    }
}
