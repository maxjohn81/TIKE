package com.mycompany.tike.model;

import java.math.BigDecimal;
import java.util.List;

public record SaleRequest(
        long eventId,
        Long clientId,
        List<SaleLineRequest> lines,
        BigDecimal expectedTotal,
        PaymentMethod paymentMethod,
        String paymentReference) {
    public SaleRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
