package com.mycompany.tike.model;

import java.util.List;

public record ReservationRequest(long eventId, Long clientId, List<ReservationLineRequest> lines) {
    public ReservationRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
