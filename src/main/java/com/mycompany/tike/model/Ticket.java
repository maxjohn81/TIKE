package com.mycompany.tike.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Ticket(
        long id,
        String saleNumber,
        String eventName,
        LocalDateTime eventStartsAt,
        String location,
        String categoryName,
        String customerName,
        BigDecimal price,
        String uniqueCode,
        String status) {
}
