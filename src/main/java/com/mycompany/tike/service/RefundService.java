package com.mycompany.tike.service;

import com.mycompany.tike.dao.RefundDao;
import com.mycompany.tike.model.RefundResult;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.SQLException;
import java.util.Locale;

public final class RefundService {
    private final RefundDao refundDao;

    public RefundService(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    public RefundResult refundFullSale(UserSession actor, String saleNumber, String reason) throws SQLException {
        requireAdministrator(actor);
        String normalizedNumber = saleNumber == null ? "" : saleNumber.trim().toUpperCase(Locale.ROOT);
        if (normalizedNumber.isEmpty() || normalizedNumber.length() > 40) {
            throw new IllegalArgumentException("Saisissez un numéro de vente valide.");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Le motif du remboursement est obligatoire.");
        }
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Le motif ne peut pas dépasser 500 caractères.");
        }
        return refundDao.refundFullSale(normalizedNumber, normalizedReason, actor.getUserId());
    }

    private static void requireAdministrator(UserSession actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN) {
            throw new SecurityException("Seuls les administrateurs peuvent enregistrer un remboursement.");
        }
    }
}
