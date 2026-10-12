package com.mycompany.tike.model;

import java.time.LocalDateTime;

public record Event(
        long id,
        String name,
        String description,
        String type,
        Long roomId,
        String roomName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime salesOpenAt,
        LocalDateTime salesCloseAt,
        EventStatus status) {
    @Override
    public String toString() {
        return name;
    }
}
