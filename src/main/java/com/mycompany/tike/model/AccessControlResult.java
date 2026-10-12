package com.mycompany.tike.model;

import java.time.LocalDateTime;

public record AccessControlResult(
        AccessControlOutcome outcome,
        String eventName,
        String ticketEventName,
        String categoryName,
        String customerName,
        String ticketCode,
        LocalDateTime checkedAt) {
}
