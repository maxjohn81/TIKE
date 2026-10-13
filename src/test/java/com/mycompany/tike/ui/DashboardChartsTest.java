package com.mycompany.tike.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.mycompany.tike.dao.ReportingDao;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardChartsTest {
    @Test
    void chartsRenderPopulatedAndEmptyStates() {
        DashboardCharts.BarChart bars = new DashboardCharts.BarChart();
        DashboardCharts.PieChart pie = new DashboardCharts.PieChart();
        bars.setSize(420, 280);
        pie.setSize(420, 280);

        assertDoesNotThrow(() -> paint(bars));
        assertDoesNotThrow(() -> paint(pie));

        bars.setValues(List.of(
                new ReportingDao.ChartValue("mai 26", new BigDecimal("120000")),
                new ReportingDao.ChartValue("juin 26", new BigDecimal("250000"))));
        pie.setValues(List.of(
                new ReportingDao.ChartValue("Concert", BigDecimal.valueOf(12)),
                new ReportingDao.ChartValue("Théâtre", BigDecimal.valueOf(5))));

        assertDoesNotThrow(() -> paint(bars));
        assertDoesNotThrow(() -> paint(pie));
    }

    private static void paint(javax.swing.JComponent component) {
        BufferedImage image = new BufferedImage(420, 280, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
    }
}
