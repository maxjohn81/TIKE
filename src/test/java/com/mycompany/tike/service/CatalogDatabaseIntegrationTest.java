package com.mycompany.tike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.tike.config.DatabaseConfig;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.model.Event;
import com.mycompany.tike.model.EventStatus;
import com.mycompany.tike.model.Room;
import com.mycompany.tike.model.TicketCategory;
import com.mycompany.tike.model.Venue;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class CatalogDatabaseIntegrationTest {
    private final CatalogDao catalogDao = new CatalogDao();

    @Test
    void catalogPersistsAndEnforcesRoomCapacityAndConflicts() throws SQLException {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("TIKE_RUN_DB_INTEGRATION")),
                "Définir TIKE_RUN_DB_INTEGRATION=1 pour lancer ce test avec SQLite.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Venue venue = null;
        Room room = null;
        Event event = null;
        try {
            venue = catalogDao.saveVenue(new Venue(0, "IT lieu " + suffix, null, "IT", null));
            room = catalogDao.saveRoom(new Room(0, venue.id(), venue.name(), "IT salle " + suffix, 100));
            LocalDateTime start = LocalDateTime.now().plusDays(45).withSecond(0).withNano(0);
            event = catalogDao.saveEvent(new Event(
                    0, "IT événement " + suffix, null, "Test", room.id(), room.toString(),
                    start, null, null, null, EventStatus.PROGRAMME));
            TicketCategory standard = catalogDao.saveCategory(new TicketCategory(
                    0, event.id(), "IT standard " + suffix, new BigDecimal("12.50"), 80, 80, null));
            Venue savedVenue = venue;
            Room savedRoom = room;
            Event savedEvent = event;

            assertEquals(venue.name(), catalogDao.saveRoom(new Room(
                    room.id(), venue.id(), venue.name(), room.name(), 100)).venueName());
            assertEquals(1, catalogDao.findEvents(suffix, EventStatus.PROGRAMME).size());
            assertEquals(80, catalogDao.findCategories(event.id()).get(0).availableQuantity());

            Event conflict = new Event(
                    0, "IT chevauchement " + suffix, null, "Test", room.id(), null,
                    start.plusMinutes(30), start.plusHours(2), null, null, EventStatus.PROGRAMME);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> catalogDao.saveEvent(conflict))
                    .getMessage().contains("déjà réservée"));

            TicketCategory overCapacity = new TicketCategory(
                    0, event.id(), "IT dépassement " + suffix, new BigDecimal("15.00"), 30, 30, null);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> catalogDao.saveCategory(overCapacity))
                    .getMessage().contains("dépasse celle de la salle"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> catalogDao.saveRoom(new Room(
                    savedRoom.id(), savedVenue.id(), savedVenue.name(), savedRoom.name(), 70)))
                    .getMessage().contains("ne peut pas être réduite"));
            assertEquals(standard.id(), catalogDao.findCategories(event.id()).get(0).id());

            SQLException foreignKey = assertThrows(
                    SQLException.class, () -> catalogDao.deleteVenue(savedVenue.id()));
            assertTrue(foreignKey.getMessage().toLowerCase(java.util.Locale.ROOT).contains("foreign key"));
        } finally {
            cleanup(venue, room, event);
        }
    }

    private static void cleanup(Venue venue, Room room, Event event) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (event != null) {
                try (PreparedStatement categories = connection.prepareStatement(
                        "DELETE FROM categories_billets WHERE evenement_id = ?")) {
                    categories.setLong(1, event.id());
                    categories.executeUpdate();
                }
                try (PreparedStatement deleteEvent = connection.prepareStatement(
                        "DELETE FROM evenements WHERE id = ?")) {
                    deleteEvent.setLong(1, event.id());
                    deleteEvent.executeUpdate();
                }
            }
            if (room != null) {
                try (PreparedStatement deleteRoom = connection.prepareStatement("DELETE FROM salles WHERE id = ?")) {
                    deleteRoom.setLong(1, room.id());
                    deleteRoom.executeUpdate();
                }
            }
            if (venue != null) {
                try (PreparedStatement deleteVenue = connection.prepareStatement("DELETE FROM lieux WHERE id = ?")) {
                    deleteVenue.setLong(1, venue.id());
                    deleteVenue.executeUpdate();
                }
            }
        }
    }
}
