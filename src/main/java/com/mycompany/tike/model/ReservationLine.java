package com.mycompany.tike.model;

import java.math.BigDecimal;

public record ReservationLine(long categoryId, String categoryName, int quantity, BigDecimal unitPrice) {
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
