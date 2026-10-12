package com.mycompany.tike.model;

public enum PaymentMethod {
    ESPECES("Espèces"),
    MOBILE_MONEY("Mobile Money"),
    CARTE("Carte"),
    VIREMENT("Virement");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
