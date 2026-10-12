package com.mycompany.tike.ui;

import com.mycompany.tike.model.UserRole;
import java.util.EnumSet;
import java.util.Set;

public enum NavigationItem {
    HOME("Accueil", EnumSet.allOf(UserRole.class)),
    EVENTS("Événements", EnumSet.of(UserRole.ADMIN, UserRole.AGENT)),
    VENUES("Lieux & salles", EnumSet.of(UserRole.ADMIN)),
    CLIENTS("Clients", EnumSet.of(UserRole.ADMIN, UserRole.AGENT)),
    SALES("Ventes", EnumSet.of(UserRole.ADMIN, UserRole.AGENT)),
    RESERVATIONS("Réservations", EnumSet.of(UserRole.ADMIN, UserRole.AGENT)),
    ACCESS_CONTROL("Contrôle d'accès", EnumSet.of(UserRole.ADMIN, UserRole.CONTROLEUR)),
    REPORTS("Rapports", EnumSet.of(UserRole.ADMIN)),
    USERS("Utilisateurs", EnumSet.of(UserRole.ADMIN)),
    HISTORY("Historique", EnumSet.of(UserRole.ADMIN)),
    SETTINGS("Paramètres", EnumSet.of(UserRole.ADMIN));

    private final String label;
    private final Set<UserRole> allowedRoles;

    NavigationItem(String label, Set<UserRole> allowedRoles) {
        this.label = label;
        this.allowedRoles = Set.copyOf(allowedRoles);
    }

    public String getLabel() {
        return label;
    }

    public boolean isAvailableTo(UserRole role) {
        return allowedRoles.contains(role);
    }
}
