package com.mycompany.tike.model;

public enum UserRole {
    ADMIN("Administrateur"),
    AGENT("Agent de billetterie"),
    CONTROLEUR("Contrôleur d'accès");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
