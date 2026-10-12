package com.mycompany.tike.model;

public enum ReservationStatus {
    EN_ATTENTE("En attente"),
    CONFIRMEE("Convertie en vente"),
    EXPIREE("Expirée"),
    ANNULEE("Annulée");

    private final String label;

    ReservationStatus(String label) {
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
