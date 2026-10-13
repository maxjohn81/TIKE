package com.mycompany.tike.service;

import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.model.Client;
import com.mycompany.tike.model.SaleHistoryItem;
import com.mycompany.tike.model.SaleLineRequest;
import com.mycompany.tike.model.SaleRequest;
import com.mycompany.tike.model.SaleResult;
import com.mycompany.tike.model.Ticket;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.model.UserSession;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SalesService {
    private final SalesDao salesDao;

    public SalesService(SalesDao salesDao) {
        this.salesDao = salesDao;
    }

    public List<Client> listClients(UserSession actor, String search) throws SQLException {
        requireSalesRole(actor);
        return salesDao.findClients(search);
    }

    public Client saveClient(UserSession actor, Client client) throws SQLException {
        requireSalesRole(actor);
        if (client == null || client.fullName() == null || client.fullName().isBlank()) {
            throw new IllegalArgumentException("Le nom complet du client est obligatoire.");
        }
        String fullName = client.fullName().trim();
        String phone = normalizePhone(client.phone());
        String email = clean(client.email());
        if (fullName.length() > 160) {
            throw new IllegalArgumentException("Le nom complet ne peut pas dépasser 160 caractères.");
        }
        if (phone != null && phone.length() > 40) {
            throw new IllegalArgumentException("Le téléphone ne peut pas dépasser 40 caractères.");
        }
        if (email != null && (email.length() > 180 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new IllegalArgumentException("Saisissez une adresse e-mail valide (facultative).");
        }
        return salesDao.saveClient(new Client(client.id(), fullName, phone, email));
    }

    public List<SaleHistoryItem> clientHistory(UserSession actor, long clientId) throws SQLException {
        requireSalesRole(actor);
        if (clientId <= 0) {
            throw new IllegalArgumentException("Sélectionnez un client.");
        }
        return salesDao.findClientHistory(clientId);
    }

    public List<SaleHistoryItem> listSales(UserSession actor) throws SQLException {
        requireSalesRole(actor);
        return salesDao.findAllSales();
    }

    public List<Ticket> findPaidSaleTickets(UserSession actor, String saleNumber) throws SQLException {
        requireSalesRole(actor);
        String normalizedNumber = saleNumber == null ? "" : saleNumber.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedNumber.isEmpty() || normalizedNumber.length() > 40) {
            throw new IllegalArgumentException("Saisissez un numéro de vente valide.");
        }
        return salesDao.findPaidSaleTickets(normalizedNumber);
    }

    public SaleResult createSale(UserSession actor, SaleRequest request) throws SQLException {
        requireSalesRole(actor);
        if (request == null || request.eventId() <= 0) {
            throw new IllegalArgumentException("Sélectionnez un événement.");
        }
        if (request.paymentMethod() == null) {
            throw new IllegalArgumentException("Sélectionnez un mode de paiement.");
        }
        if (request.expectedTotal() == null || request.expectedTotal().signum() < 0
                || request.expectedTotal().scale() > 2 || request.expectedTotal().precision() > 12) {
            throw new IllegalArgumentException("Le montant total attendu est invalide.");
        }
        if (request.lines().isEmpty()) {
            throw new IllegalArgumentException("Ajoutez au moins une catégorie au panier.");
        }
        Set<Long> categories = new HashSet<>();
        for (SaleLineRequest line : request.lines()) {
            if (line.categoryId() <= 0) {
                throw new IllegalArgumentException("Une catégorie sélectionnée est invalide.");
            }
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("La quantité doit être supérieure à zéro.");
            }
            if (!categories.add(line.categoryId())) {
                throw new IllegalArgumentException("Une catégorie ne peut apparaître qu'une fois dans le panier.");
            }
        }
        String reference = clean(request.paymentReference());
        if (reference != null && reference.length() > 120) {
            throw new IllegalArgumentException("La référence de paiement ne peut pas dépasser 120 caractères.");
        }
        return salesDao.createSale(new SaleRequest(
                request.eventId(), request.clientId(), request.lines(), request.expectedTotal(),
                request.paymentMethod(), reference),
                actor.getUserId());
    }

    private static void requireSalesRole(UserSession actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.AGENT)) {
            throw new SecurityException("Cette opération est réservée aux administrateurs et aux agents de billetterie.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizePhone(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.replaceAll("[\\s().-]", "");
    }
}
