package com.mycompany.tike.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Reservation(
        long id,
        String number,
        long eventId,
        String eventName,
        Long clientId,
        String clientName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        ReservationStatus status,
        BigDecimal total,
        List<ReservationLine> lines) {
    public Reservation {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
