# TIKE

Application de bureau Java Swing pour la gestion de billetterie et le contrôle d'accès.

## Prérequis

- JDK 17 ou supérieur
- Maven 3.9 ou supérieur
- PostgreSQL

## Configuration PostgreSQL

1. Créez une base vide nommée `tike` dans PostgreSQL.
2. Copiez `db.example.properties` en `db.properties`, puis renseignez l'utilisateur et le mot de passe PostgreSQL locaux. `db.properties` est ignoré par Git.
3. Depuis la racine du projet, appliquez `schema.sql` avec pgAdmin ou `psql -U <utilisateur> -d tike -f schema.sql`.
4. Lancez `demo-data.sql` uniquement si vous souhaitez insérer des données fictives. Ce script ne crée aucun compte ni mot de passe.

Les variables d'environnement `DB_URL`, `DB_USER` et `DB_PASSWORD` peuvent remplacer les valeurs du fichier local. Après une mise à jour du projet qui modifie le schéma, réappliquez `schema.sql` à la base existante ; les instructions de création sont idempotentes.

## Lieux, salles et événements

La phase 4 permet aux administrateurs de gérer les lieux, les salles, les événements et leurs catégories de billets. Les agents peuvent consulter et filtrer les événements ainsi que les disponibilités des catégories, sans les modifier. Les identifiants affichés sont générés automatiquement par PostgreSQL.

La capacité d'une salle limite la quantité totale configurée pour les catégories de chacun de ses événements. Deux événements actifs ne peuvent pas occuper la même salle sur des plages horaires qui se chevauchent. Si l'heure de fin d'un événement est laissée vide, TIKE réserve une heure à partir de son heure de début pour vérifier ces conflits. Les événements annulés ou terminés ne bloquent pas une salle.

## Clients et ventes

Les administrateurs et agents peuvent ajouter, modifier et rechercher des clients, consulter leur historique de ventes, et enregistrer une vente pour un événement au statut `EN_VENTE`. Le stock disponible d'une catégorie est calculé comme sa quantité totale moins les billets liés aux ventes payées dont le billet n'est ni annulé ni remboursé. Les ventes concurrentes sont sérialisées par événement afin qu'un dernier billet ne puisse pas être vendu deux fois.

Une vente confirmée enregistre ensemble la vente, ses lignes, le paiement déclaré et un billet avec un code UUID unique par entrée. En cas d'échec d'une étape, toute la transaction est annulée. Les modes espèces, Mobile Money, carte et virement sont des informations déclaratives : TIKE ne contacte aucun prestataire et n'effectue aucun paiement électronique. L'opérateur doit confirmer l'encaissement avant de valider une vente payante. Les catégories gratuites sont vendues sans ligne de paiement.

## Réservations

Les administrateurs et agents peuvent réserver des catégories pour un client sur un événement à venir. Une réservation bloque le stock pendant 24 heures au maximum, ou jusqu'à l'heure de début de l'événement si celle-ci arrive plus tôt. Les réservations expirées sont marquées lors de l'ouverture/actualisation du module, avant une nouvelle réservation ou avant une vente, et cessent alors de bloquer le stock. L'annulation libère également les quantités, sans supprimer l'historique.

Une réservation en attente ne crée ni billet ni paiement. Sa conversion en vente nécessite un événement encore en vente et la confirmation de l'encaissement ; la vente, le paiement déclaré, les billets et le statut « Convertie en vente » sont enregistrés ensemble. Une réservation ne peut être convertie qu'une seule fois. Les prix sont conservés comme montant indicatif à la réservation ; si un tarif change avant la conversion, la conversion est refusée et la réservation reste en attente.

## Billets et export PDF

Chaque billet créé avec une vente possède son propre code UUID unique, enregistré avec le billet dans la même transaction que la vente. Le QR Code ne contient que cet identifiant, sans nom ni autre donnée personnelle. Après une vente, l'application affiche l'aperçu des billets et permet de les enregistrer dans un PDF, avec une page par billet. Le PDF présente l'événement, sa date, le lieu, la catégorie, le titulaire, le prix, le statut et le QR Code.

Pour réimprimer ultérieurement, ouvrez **Ventes**, saisissez le numéro de vente dans **Billets et réimpression**, puis choisissez **Afficher / réimprimer**. Cette opération relit les billets existants et ne crée ni ne modifie aucun billet.

## Contrôle d'accès

Les administrateurs et contrôleurs peuvent ouvrir **Contrôle d'accès**, choisir l'événement et saisir l'UUID imprimé sous le QR Code. Un premier contrôle valide marque atomiquement le billet comme utilisé et enregistre le contrôleur, l'heure et le résultat. Une seconde tentative est refusée. L'écran distingue les billets valides, déjà utilisés, non payés, annulés/remboursés, introuvables et ceux associés à un autre événement. Les billets gratuits sont admis si leur vente est confirmée comme payée ; un montant positif doit être intégralement enregistré. La lecture par caméra n'est pas incluse : le code est saisi manuellement.

## Annulations et remboursements

La règle de la phase 9 est le remboursement intégral d'une vente payée, sans remboursement partiel. Seuls les administrateurs peuvent l'enregistrer, après avoir effectué le remboursement hors de TIKE. Le motif est obligatoire. Un remboursement est refusé si l'événement a commencé, si la vente ou ses paiements ne sont pas entièrement payés, ou si au moins un billet n'est plus valide (notamment s'il a déjà été utilisé). L'opération est atomique : elle conserve une entrée d'audit dans `remboursements`, marque la vente et ses paiements comme remboursés, invalide ses billets et libère le stock correspondant.

## Tableau de bord, rapports et historique

Le tableau de bord affiche les événements programmés/en vente/complets, les billets des ventes payées, les revenus des ventes encore payées (les ventes remboursées sont exclues) et les réservations non expirées en attente. Il actualise ses données à l'ouverture de l'accueil.

Les administrateurs disposent de **Rapports** : ventes et taux de remplissage filtrables par dates et événement, avec export CSV UTF-8. Le taux de remplissage est le nombre de billets payés encore valides ou utilisés rapporté à la capacité cumulée des catégories. L'écran **Historique** filtre les actions par dates et texte et présente jusqu'aux 500 entrées les plus récentes. L'export CSV échappe les champs et protège contre l'exécution de formules dans un tableur. Les ventes, remboursements, réservations et tentatives de contrôle d'accès réussies ou refusées sont consignées dans `journal_activites` dans la transaction métier correspondante.

## Vérification

Depuis la racine du projet :

```powershell
mvn test
mvn exec:java
```

Au premier lancement, TIKE demande de créer le compte administrateur initial. Le nom d'utilisateur et le mot de passe sont conservés dans PostgreSQL (mot de passe haché avec BCrypt). Ensuite, l'écran de connexion authentifie les comptes actifs et ouvre les menus selon le rôle.

Les rapports et l'historique sont réservés au rôle administrateur ; le tableau de bord est accessible aux rôles applicatifs et n'affiche pas de chiffres fictifs.

Les tests PostgreSQL (authentification, catalogue, clients, ventes et réservations atomiques, concurrence de stock, remboursements, contrôle d'accès, rapports et nettoyage des données de test) sont optionnels :

```powershell
$env:TIKE_RUN_DB_INTEGRATION = "1"
& "C:\tools\apache-maven-3.9.16\bin\mvn.cmd" "-Dtest=AuthenticationDatabaseIntegrationTest,CatalogDatabaseIntegrationTest,SalesDatabaseIntegrationTest,ReservationDatabaseIntegrationTest,AccessControlDatabaseIntegrationTest,RefundDatabaseIntegrationTest" test
Remove-Item Env:\TIKE_RUN_DB_INTEGRATION
```

Les tests suppriment leurs données temporaires à la fin.#   T I K E  
 