package com.mycompany.tike.service;

import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class ReportingService {
    private final ReportingDao reportingDao;

    public ReportingService(ReportingDao reportingDao) {
        this.reportingDao = reportingDao;
    }

    public ReportingDao.DashboardMetrics dashboard(UserSession actor) throws SQLException {
        if (actor == null) {
            throw new SecurityException("Une session authentifiée est nécessaire.");
        }
        return reportingDao.dashboardMetrics();
    }

    public List<ReportingDao.ChartValue> monthlyRevenue(UserSession actor) throws SQLException {
        requireAuthenticated(actor);
        return reportingDao.monthlyRevenue();
    }

    public List<ReportingDao.ChartValue> ticketDistribution(UserSession actor) throws SQLException {
        requireAuthenticated(actor);
        return reportingDao.ticketDistributionByEvent();
    }

    public List<ReportingDao.EventOption> events(UserSession actor) throws SQLException {
        requireAdmin(actor);
        return reportingDao.findEventOptions();
    }

    public List<ReportingDao.SalesReportRow> sales(
            UserSession actor, LocalDate from, LocalDate through, Long eventId) throws SQLException {
        requireAdmin(actor);
        validateRange(from, through);
        return reportingDao.findSales(from, through, eventId);
    }

    public List<ReportingDao.EventFillRow> eventFill(
            UserSession actor, LocalDate from, LocalDate through, Long eventId) throws SQLException {
        requireAdmin(actor);
        validateRange(from, through);
        return reportingDao.findEventFill(from, through, eventId);
    }

    public List<ReportingDao.ActivityEntry> activity(
            UserSession actor, LocalDate from, LocalDate through, String search) throws SQLException {
        requireAdmin(actor);
        validateRange(from, through);
        return reportingDao.findActivity(from, through, search);
    }

    private static void validateRange(LocalDate from, LocalDate through) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La date de début doit précéder la date de fin.");
        }
    }

    private static void requireAdmin(UserSession actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN) {
            throw new SecurityException("Cette fonctionnalité est réservée aux administrateurs.");
        }
    }

    private static void requireAuthenticated(UserSession actor) {
        if (actor == null) {
            throw new SecurityException("Une session authentifiée est nécessaire.");
        }
    }
}
