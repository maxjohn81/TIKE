package com.mycompany.tike.service;

import com.mycompany.tike.dao.AccessControlDao;
import com.mycompany.tike.model.AccessControlEvent;
import com.mycompany.tike.model.AccessControlOutcome;
import com.mycompany.tike.model.AccessControlResult;
import com.mycompany.tike.model.SaleHistoryItem;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AccessControlService {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private final AccessControlDao accessControlDao;

    public AccessControlService(AccessControlDao accessControlDao) {
        this.accessControlDao = accessControlDao;
    }

    public List<AccessControlEvent> listEvents(UserSession actor) throws SQLException {
        requireAccessRole(actor);
        return accessControlDao.findEvents();
    }

    public List<SaleHistoryItem> listSales(UserSession actor, long eventId) throws SQLException {
        requireAccessRole(actor);
        if (eventId <= 0) {
            throw new IllegalArgumentException("Sélectionnez l'événement à contrôler.");
        }
        return accessControlDao.findSalesForEvent(eventId);
    }

    public List<AccessControlDao.TicketChoice> listSaleTickets(
            UserSession actor, long eventId, String saleNumber) throws SQLException {
        requireAccessRole(actor);
        if (eventId <= 0) {
            throw new IllegalArgumentException("Sélectionnez l'événement à contrôler.");
        }
        if (saleNumber == null || saleNumber.isBlank()) {
            throw new IllegalArgumentException("Sélectionnez une vente.");
        }
        return accessControlDao.findTicketsForSale(eventId, saleNumber.trim());
    }

    public AccessControlResult check(UserSession actor, long eventId, String rawCode) throws SQLException {
        requireAccessRole(actor);
        if (eventId <= 0) {
            throw new IllegalArgumentException("Sélectionnez l'événement à contrôler.");
        }
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Saisissez le code du billet.");
        }
        UUID parsedCode = UUID_PATTERN.matcher(code).matches()
                ? UUID.fromString(code.toLowerCase(Locale.ROOT))
                : null;
        return accessControlDao.check(eventId, parsedCode, actor.getUserId());
    }

    private static void requireAccessRole(UserSession actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.CONTROLEUR)) {
            throw new SecurityException("Cette opération est réservée aux administrateurs et aux contrôleurs d'accès.");
        }
    }
}
