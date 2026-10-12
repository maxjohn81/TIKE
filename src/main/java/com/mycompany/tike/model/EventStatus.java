package com.mycompany.tike.model;

public enum EventStatus {
    BROUILLON("Brouillon"),
    PROGRAMME("Programmé"),
    EN_VENTE("En vente"),
    COMPLET("Complet"),
    ANNULE("Annulé"),
    TERMINE("Terminé");

    private final String label;

    EventStatus(String label) {
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
