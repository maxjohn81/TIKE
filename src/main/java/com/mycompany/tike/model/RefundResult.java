package com.mycompany.tike.model;

import java.math.BigDecimal;

public record RefundResult(String saleNumber, String refundNumber, BigDecimal amount, int ticketCount) {
}
