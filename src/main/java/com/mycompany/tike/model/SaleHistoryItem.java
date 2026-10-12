package com.mycompany.tike.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleHistoryItem(
        String saleNumber,
        String eventName,
        LocalDateTime soldAt,
        BigDecimal total,
        String status,
        int ticketCount) {
}
