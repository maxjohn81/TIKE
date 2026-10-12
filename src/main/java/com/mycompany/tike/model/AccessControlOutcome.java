package com.mycompany.tike.model;

public enum AccessControlOutcome {
    VALIDE("VALIDE — Accès autorisé"),
    DEJA_UTILISE("DÉJÀ UTILISÉ — Accès refusé"),
    NON_TROUVE("INTROUVABLE — Aucun billet correspondant"),
    NON_PAYE("NON PAYÉ — Accès refusé"),
    ANNULE("ANNULÉ / REMBOURSÉ — Accès refusé"),
    MAUVAIS_EVENEMENT("MAUVAIS ÉVÉNEMENT — Accès refusé");

    private final String label;

    AccessControlOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
