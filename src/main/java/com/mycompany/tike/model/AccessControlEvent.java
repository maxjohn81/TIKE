package com.mycompany.tike.model;

import java.time.LocalDateTime;

public record AccessControlEvent(long id, String name, LocalDateTime startsAt, EventStatus status) {
    @Override
    public String toString() {
        String date = startsAt == null ? "" : startsAt.format(java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy HH:mm"));
        return name + (date.isEmpty() ? "" : " — " + date) + " (" + status.getLabel() + ")";
    }
}
