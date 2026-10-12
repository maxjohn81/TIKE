package com.mycompany.tike.service;

import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public final class CatalogService {
    private final CatalogDao catalogDao;

    public CatalogService(CatalogDao catalogDao) {
        this.catalogDao = catalogDao;
    }

    public List<Venue> listVenues(UserSession actor, String search) throws SQLException {
        requireAdmin(actor);
        return catalogDao.findVenues(search);
    }

    public Venue saveVenue(UserSession actor, Venue venue) throws SQLException {
        requireAdmin(actor);
        validateRequired(venue.name(), "Le nom du lieu");
        validateLength(venue.name(), 120, "Le nom du lieu");
        validateLength(venue.city(), 100, "La ville");
        return catalogDao.saveVenue(new Venue(
                venue.id(), venue.name().trim(), clean(venue.address()), clean(venue.city()), clean(venue.description())));
    }

    public void deleteVenue(UserSession actor, long venueId) throws SQLException {
        requireAdmin(actor);
        catalogDao.deleteVenue(venueId);
    }

    public List<Room> listRooms(UserSession actor, String search) throws SQLException {
        requireAdmin(actor);
        return catalogDao.findRooms(search);
    }

    public Room saveRoom(UserSession actor, Room room) throws SQLException {
        requireAdmin(actor);
        if (room.venueId() <= 0) {
            throw new IllegalArgumentException("Sélectionnez un lieu.");
        }
        validateRequired(room.name(), "Le nom de la salle");
        validateLength(room.name(), 120, "Le nom de la salle");
        if (room.capacity() <= 0) {
            throw new IllegalArgumentException("La capacité doit être supérieure à zéro.");
        }
        return catalogDao.saveRoom(new Room(room.id(), room.venueId(), room.venueName(), room.name().trim(),
                room.capacity()));
    }

    public void deleteRoom(UserSession actor, long roomId) throws SQLException {
        requireAdmin(actor);
        catalogDao.deleteRoom(roomId);
    }

    public List<Event> listEvents(UserSession actor, String search, EventStatus status) throws SQLException {
        requireRole(actor, UserRole.ADMIN, UserRole.AGENT);
        return catalogDao.findEvents(search, status);
    }

    public Event saveEvent(UserSession actor, Event event) throws SQLException {
        requireAdmin(actor);
        validateRequired(event.name(), "Le nom de l'événement");
        validateLength(event.name(), 180, "Le nom de l'événement");
        validateRequired(event.type(), "Le type d'événement");
        validateLength(event.type(), 80, "Le type d'événement");
        if (event.startsAt() == null) {
            throw new IllegalArgumentException("La date de début est obligatoire.");
        }
        if (event.endsAt() != null && !event.endsAt().isAfter(event.startsAt())) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        if (event.salesOpenAt() != null && event.salesCloseAt() != null
                && !event.salesCloseAt().isAfter(event.salesOpenAt())) {
            throw new IllegalArgumentException("La fermeture des ventes doit être après leur ouverture.");
        }
        if (event.salesOpenAt() != null && event.salesOpenAt().isAfter(event.startsAt())) {
            throw new IllegalArgumentException("Les ventes ne peuvent pas commencer après le début de l'événement.");
        }
        if (event.salesCloseAt() != null && event.salesCloseAt().isAfter(event.startsAt())) {
            throw new IllegalArgumentException("Les ventes doivent fermer au plus tard au début de l'événement.");
        }
        if (event.status() == null) {
            throw new IllegalArgumentException("Sélectionnez le statut de l'événement.");
        }
        return catalogDao.saveEvent(new Event(
                event.id(),
                event.name().trim(),
                clean(event.description()),
                event.type().trim(),
                event.roomId(),
                event.roomName(),
                event.startsAt(),
                event.endsAt(),
                event.salesOpenAt(),
                event.salesCloseAt(),
                event.status()));
    }

    public List<TicketCategory> listCategories(UserSession actor, long eventId) throws SQLException {
        requireRole(actor, UserRole.ADMIN, UserRole.AGENT);
        return catalogDao.findCategories(eventId);
    }

    public TicketCategory saveCategory(UserSession actor, TicketCategory category) throws SQLException {
        requireAdmin(actor);
        validateRequired(category.name(), "Le nom de la catégorie");
        validateLength(category.name(), 80, "Le nom de la catégorie");
        if (category.eventId() <= 0) {
            throw new IllegalArgumentException("Sélectionnez un événement.");
        }
        if (category.price() == null || category.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix doit être supérieur ou égal à zéro.");
        }
        if (category.price().scale() > 2 || category.price().precision() > 12) {
            throw new IllegalArgumentException("Le prix ne peut pas dépasser 10 chiffres et 2 décimales.");
        }
        if (category.totalQuantity() < 0) {
            throw new IllegalArgumentException("La quantité totale ne peut pas être négative.");
        }
        return catalogDao.saveCategory(new TicketCategory(
                category.id(), category.eventId(), category.name().trim(), category.price(),
                category.totalQuantity(), category.totalQuantity(), clean(category.description())));
    }

    public void deleteCategory(UserSession actor, long eventId, long categoryId) throws SQLException {
        requireAdmin(actor);
        catalogDao.deleteCategory(eventId, categoryId);
    }

    private static void requireAdmin(UserSession actor) {
        requireRole(actor, UserRole.ADMIN);
    }

    private static void requireRole(UserSession actor, UserRole... roles) {
        if (actor == null) {
            throw new SecurityException("Une session authentifiée est requise.");
        }
        for (UserRole role : roles) {
            if (actor.getRole() == role) {
                return;
            }
        }
        throw new SecurityException("Cette opération n'est pas autorisée pour votre rôle.");
    }

    private static void validateRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " est obligatoire.");
        }
    }

    private static void validateLength(String value, int maximum, String label) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(label + " ne peut pas dépasser " + maximum + " caractères.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
