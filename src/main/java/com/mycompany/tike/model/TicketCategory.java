package com.mycompany.tike.model;

import java.math.BigDecimal;

public record TicketCategory(
        long id,
        long eventId,
        String name,
        BigDecimal price,
        int totalQuantity,
        int availableQuantity,
        String description) {
    @Override
    public String toString() {
        return name;
    }
}
