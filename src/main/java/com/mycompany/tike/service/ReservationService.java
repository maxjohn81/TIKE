package com.mycompany.tike.service;

import com.mycompany.tike.dao.ReservationDao;
import com.mycompany.tike.model.PaymentMethod;
import com.mycompany.tike.model.Reservation;
import com.mycompany.tike.model.ReservationLineRequest;
import com.mycompany.tike.model.ReservationRequest;
import com.mycompany.tike.model.ReservationStatus;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReservationService {
    public static final int DEFAULT_EXPIRATION_HOURS = ReservationDao.DEFAULT_EXPIRATION_HOURS;
    private final ReservationDao reservationDao;

    public ReservationService(ReservationDao reservationDao) {
        this.reservationDao = reservationDao;
    }

    public List<Reservation> list(UserSession actor, String search, ReservationStatus status) throws SQLException {
        requireSalesRole(actor);
        return reservationDao.findAll(search, status);
    }

    public Reservation create(UserSession actor, ReservationRequest request) throws SQLException {
        requireSalesRole(actor);
        if (request == null || request.eventId() <= 0) {
            throw new IllegalArgumentException("Sélectionnez un événement.");
        }
        if (request.clientId() == null || request.clientId() <= 0) {
            throw new IllegalArgumentException("Sélectionnez un client pour cette réservation.");
        }
        if (request.lines().isEmpty()) {
            throw new IllegalArgumentException("Ajoutez au moins une catégorie à la réservation.");
        }
        Set<Long> categories = new HashSet<>();
        for (ReservationLineRequest line : request.lines()) {
            if (line.categoryId() <= 0) {
                throw new IllegalArgumentException("Une catégorie sélectionnée est invalide.");
            }
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("La quantité doit être supérieure à zéro.");
            }
            if (!categories.add(line.categoryId())) {
                throw new IllegalArgumentException("Une catégorie ne peut apparaître qu'une fois dans la réservation.");
            }
        }
        return reservationDao.create(request, actor.getUserId());
    }

    public void cancel(UserSession actor, long reservationId) throws SQLException {
        requireSalesRole(actor);
        if (reservationId <= 0) {
            throw new IllegalArgumentException("Sélectionnez une réservation.");
        }
        reservationDao.cancel(reservationId, actor.getUserId());
    }

    public SaleResult convert(
            UserSession actor, long reservationId, PaymentMethod method, String reference) throws SQLException {
        requireSalesRole(actor);
        if (reservationId <= 0) {
            throw new IllegalArgumentException("Sélectionnez une réservation.");
        }
        if (method == null) {
            throw new IllegalArgumentException("Sélectionnez un mode de paiement.");
        }
        String cleanReference = reference == null || reference.isBlank() ? null : reference.trim();
        if (cleanReference != null && cleanReference.length() > 120) {
            throw new IllegalArgumentException("La référence de paiement ne peut pas dépasser 120 caractères.");
        }
        return reservationDao.convert(reservationId, method, cleanReference, actor.getUserId());
    }

    public void expirePending(UserSession actor) throws SQLException {
        requireSalesRole(actor);
        reservationDao.expirePending();
    }

    private static void requireSalesRole(UserSession actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.AGENT)) {
            throw new SecurityException("Cette opération est réservée aux administrateurs et aux agents de billetterie.");
        }
    }
}
