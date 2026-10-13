# Scénario de démonstration TIKE

## Préparation

1. Démarrer TIKE avec `mvn compile exec:java`. Sur une base neuve, créer le premier compte administrateur dans l'application. Le fichier SQLite se trouve par défaut dans `%LOCALAPPDATA%\TIKE\tike.db`.
2. Pour charger les données malgaches, ouvrir ce fichier avec un outil SQLite et exécuter `demo-data.sql`. Le script ajoute jusqu'à 20 lignes par table et peut être rejoué sans dupliquer les fixtures.
3. Se connecter comme administrateur et vérifier que les événements de démonstration sont en vente et qu'il reste du stock.

Pour une démonstration répétable, utiliser une base locale dédiée, jamais une base contenant les données de production.

## Déroulé conseillé

1. **Présenter les rôles.** Montrer les sections accessibles à l'administrateur. Dans **Utilisateurs**, créer un agent et un contrôleur avec des identifiants temporaires non réutilisés en production.
2. **Présenter le catalogue.** Ouvrir l'événement de démonstration, sa salle et ses catégories. Vérifier la date future, le statut `EN_VENTE` et le stock disponible.
3. **Enregistrer une vente.** Dans **Ventes**, choisir l'événement, le client « Client Démo » et une catégorie STANDARD. Vendre un billet à 20 000 Ar avec un mode de paiement déclaré, après confirmation de l'encaissement hors application. TIKE n'exécute pas le paiement.
4. **Montrer le billet.** Vérifier le numéro de vente, le billet créé et son code unique, puis ouvrir l'aperçu et enregistrer le PDF.
5. **Contrôler par caméra.** Démarrer Iriun sur le PC et le téléphone, le sélectionner dans **Contrôle d'accès > Scanner un QR Code**, puis présenter le QR du billet à la caméra. Le scan remplit le code et lance la validation en base. La première validation doit être `VALIDE`; rescanner le même billet pour montrer le refus `DEJA_UTILISE`. La saisie manuelle reste disponible.
6. **Présenter le suivi.** Se reconnecter comme administrateur et consulter le tableau de bord, **Rapports** et **Historique**. Montrer les ventes, les revenus en Ariary et les opérations consignées.
7. **Terminer par les limites.** Préciser les fonctions hors périmètre indiquées ci-dessous et rappeler de ne pas effectuer de remboursement réel pendant la démonstration.

## Bilan fonctionnel et limites

### Fonctionnalités réalisées

- Authentification par comptes actifs, mots de passe hachés, rôles ADMIN/AGENT/CONTROLEUR et administration des utilisateurs.
- Gestion des lieux, salles, événements, catégories, clients, ventes et réservations, avec validations et persistance locale SQLite.
- Vente et paiement déclaratif enregistrés transactionnellement, contrôle du stock, génération de billets uniques, aperçu/PDF et réimpression.
- Contrôle manuel ou par scan caméra d'un billet et refus d'une seconde utilisation.
- Remboursement intégral réservé à l'administrateur, enregistré après l'exécution du remboursement hors TIKE.
- Tableau de bord, graphiques, rapports filtrables, exports CSV et journal d'activité.

### Limites connues

- Le scan caméra nécessite un périphérique détecté par Windows et une caméra démarrée ; l'utilisation d'Iriun 2.9.8 dépend de la reconnaissance de sa caméra virtuelle sur le poste cible. La sélection ou la saisie manuelle restent disponibles en secours.
- Les modes Mobile Money, carte et virement sont déclaratifs : aucun prestataire ni paiement électronique n'est intégré.
- Les remboursements partiels ne sont pas pris en charge et TIKE ne déclenche pas le transfert d'argent.
- L'expiration des réservations est vérifiée à l'ouverture/actualisation des écrans concernés ou avant une opération ; aucun ordonnanceur en arrière-plan n'est inclus.
- La base est locale à chaque PC et n'est pas partagée automatiquement entre plusieurs postes. L'affichage doit être revérifié sur les résolutions et facteurs de mise à l'échelle utilisés pour la soutenance.
