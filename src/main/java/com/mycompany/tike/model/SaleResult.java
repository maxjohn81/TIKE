package com.mycompany.tike.model;

import java.math.BigDecimal;

public record SaleResult(long id, String number, BigDecimal total, int ticketCount) {
}
