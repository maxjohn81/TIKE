# TIKE

Application de bureau Java Swing pour la gestion de billetterie et le contrôle d'accès.

## Prérequis

- JDK 17 ou supérieur
- Maven 3.9 ou supérieur

## Base locale SQLite

TIKE utilise uniquement SQLite et ne nécessite aucun serveur de base de données. Au premier démarrage, l'application initialise le schéma et crée `%LOCALAPPDATA%\TIKE\tike.db`. Lancez-la avec `mvn compile exec:java` et créez le premier compte administrateur dans l'application.

`TIKE_DB_PATH` peut définir un autre emplacement pour le fichier. Le schéma SQLite est maintenu dans `src/main/resources/schema-sqlite.sql` et initialisé automatiquement.

Le script `demo-data.sql` fournit des données de démonstration malgaches compatibles SQLite. Exécutez-le sur une base de démonstration après initialisation du schéma ; les comptes d'exemple sont désactivés et doivent recevoir un nouveau mot de passe avant activation.

## Lieux, salles et événements

La phase 4 permet aux administrateurs de gérer les lieux, les salles, les événements et leurs catégories de billets. Les agents peuvent consulter et filtrer les événements ainsi que les disponibilités des catégories, sans les modifier. Les identifiants affichés sont générés automatiquement par SQLite.

La capacité d'une salle limite la quantité totale configurée pour les catégories de chacun de ses événements. Deux événements actifs ne peuvent pas occuper la même salle sur des plages horaires qui se chevauchent. Si l'heure de fin d'un événement est laissée vide, TIKE réserve une heure à partir de son heure de début pour vérifier ces conflits. Les événements annulés ou terminés ne bloquent pas une salle.

## Clients et ventes

Les administrateurs et agents peuvent ajouter, modifier et rechercher des clients, consulter leur historique de ventes, et enregistrer une vente pour un événement au statut `EN_VENTE`. La page Ventes affiche désormais en bas la liste de toutes les ventes enregistrées, avec le nom complet du client. La recherche de vente pour la réimpression et le remboursement filtre les ventes payées par nom du client, événement ou numéro ; choisir un résultat renseigne le numéro automatiquement. Le bouton **Vider les champs** réinitialise le formulaire et le panier ; après une vente réussie, le formulaire est réinitialisé automatiquement. Le stock disponible d'une catégorie est calculé comme sa quantité totale moins les billets liés aux ventes payées dont le billet n'est ni annulé ni remboursé. Les ventes concurrentes sont sérialisées par événement afin qu'un dernier billet ne puisse pas être vendu deux fois.

Une vente confirmée enregistre ensemble la vente, ses lignes, le paiement déclaré et un billet avec un code UUID unique par entrée. En cas d'échec d'une étape, toute la transaction est annulée. Les modes espèces, Mobile Money, carte et virement sont des informations déclaratives : TIKE ne contacte aucun prestataire et n'effectue aucun paiement électronique. L'opérateur doit confirmer l'encaissement avant de valider une vente payante. Les catégories gratuites sont vendues sans ligne de paiement.

## Réservations

Les administrateurs et agents peuvent réserver des catégories pour un client sur un événement à venir. Le bouton **Vider les champs** réinitialise le client, l'événement, la quantité et le panier de création. Une réservation bloque le stock pendant 24 heures au maximum, ou jusqu'à l'heure de début de l'événement si celle-ci arrive plus tôt. Les réservations expirées sont marquées lors de l'ouverture/actualisation du module, avant une nouvelle réservation ou avant une vente, et cessent alors de bloquer le stock. L'annulation libère également les quantités, sans supprimer l'historique.

Une réservation en attente ne crée ni billet ni paiement. Sa conversion en vente nécessite un événement encore en vente et la confirmation de l'encaissement ; la vente, le paiement déclaré, les billets et le statut « Convertie en vente » sont enregistrés ensemble. Une réservation ne peut être convertie qu'une seule fois. Les prix sont conservés comme montant indicatif à la réservation ; si un tarif change avant la conversion, la conversion est refusée et la réservation reste en attente.

## Billets et export PDF

Chaque billet créé avec une vente possède son propre code UUID unique, enregistré avec le billet dans la même transaction que la vente. Le QR Code ne contient que cet identifiant, sans nom ni autre donnée personnelle. Après une vente, l'application affiche l'aperçu des billets et permet de les enregistrer dans un PDF, avec une page par billet. Le PDF présente l'événement, sa date, le lieu, la catégorie, le titulaire, le prix, le statut et le QR Code.

Pour réimprimer ultérieurement, ouvrez **Ventes**, saisissez le numéro de vente dans **Billets et réimpression**, puis choisissez **Afficher / réimprimer**. Cette opération relit les billets existants et ne crée ni ne modifie aucun billet.

## Contrôle d'accès

Les administrateurs et contrôleurs peuvent ouvrir **Contrôle d'accès**, choisir l'événement, rechercher une vente par nom du client puis choisir un billet de cette vente pour renseigner automatiquement son UUID. Le code peut être saisi manuellement ou lu avec **Scanner un QR Code** et une caméra détectée par Windows. Pour utiliser un téléphone via Iriun Webcam 2.9.8, installer et démarrer Iriun sur le PC et le téléphone, autoriser la caméra, puis choisir le périphérique Iriun dans la liste des caméras de TIKE. Le téléphone et le PC doivent être reliés selon le mode pris en charge par Iriun ; cette intégration dépend de la détection de la caméra virtuelle par le pilote Java et doit être validée sur le poste cible. Le QR lu est soumis au même contrôle métier et à la même vérification en base que la saisie manuelle : événement, paiement, statut et usage précédent. Un premier contrôle valide marque atomiquement le billet comme utilisé ; une seconde tentative est refusée. Les billets gratuits sont admis si leur vente est confirmée comme payée ; un montant positif doit être intégralement enregistré.

## Annulations et remboursements

La règle de la phase 9 est le remboursement intégral d'une vente payée, sans remboursement partiel. Seuls les administrateurs peuvent l'enregistrer, après avoir effectué le remboursement hors de TIKE. Le motif est obligatoire. Un remboursement est refusé si l'événement a commencé, si la vente ou ses paiements ne sont pas entièrement payés, ou si au moins un billet n'est plus valide (notamment s'il a déjà été utilisé). L'opération est atomique : elle conserve une entrée d'audit dans `remboursements`, marque la vente et ses paiements comme remboursés, invalide ses billets et libère le stock correspondant.

## Tableau de bord, rapports et historique

Le tableau de bord affiche les événements programmés/en vente/complets, les billets des ventes payées, les revenus en Ariary (Ar) des ventes encore payées (les ventes remboursées sont exclues) et les réservations non expirées en attente. Il actualise ses données à l'ouverture de l'accueil. Il comprend un graphique en bâtons des revenus mensuels sur six mois et un graphique circulaire de la répartition des billets payés par événement (les cinq principaux événements puis les autres).

Les administrateurs disposent de **Rapports** : ventes et taux de remplissage filtrables par dates et événement, avec export CSV UTF-8. Le taux de remplissage est le nombre de billets payés encore valides ou utilisés rapporté à la capacité cumulée des catégories. L'écran **Historique** filtre les actions par dates et texte et présente jusqu'aux 500 entrées les plus récentes. L'export CSV échappe les champs et protège contre l'exécution de formules dans un tableur. Les ventes, remboursements, réservations et tentatives de contrôle d'accès réussies ou refusées sont consignées dans `journal_activites` dans la transaction métier correspondante.

## Démonstration et périmètre

Le scénario de démonstration, les données à préparer et le bilan des fonctionnalités/limites sont décrits dans [SCENARIO_DEMONSTRATION.md](./SCENARIO_DEMONSTRATION.md).

## Vérification

Depuis la racine du projet :

```powershell
mvn clean test
mvn compile exec:java
```

Lancez toujours l'application avec `mvn compile exec:java` pour que Maven compile les dernières sources avant le démarrage. L'objectif `exec:java` exécuté seul ne déclenche pas automatiquement la compilation et peut réutiliser des classes obsolètes ou manquantes dans `target/classes` (par exemple `UserCredential`). Si le problème persiste, arrêtez l'application puis exécutez `mvn clean compile exec:java`.

Au premier lancement d'une base SQLite neuve, TIKE demande de créer le compte administrateur initial. Le mot de passe est conservé sous forme de hash BCrypt. Ensuite, l'écran de connexion authentifie les comptes actifs et ouvre les menus selon le rôle.

Les rapports et l'historique sont réservés au rôle administrateur ; le tableau de bord est accessible aux rôles applicatifs et n'affiche pas de chiffres fictifs.

Les tests d'intégration SQLite (authentification, catalogue, clients, ventes et réservations atomiques, concurrence de stock, remboursements, contrôle d'accès et rapports) utilisent un fichier temporaire :

```powershell
$env:TIKE_RUN_DB_INTEGRATION = "1"
$env:TIKE_DB_PATH = Join-Path $env:TEMP ("tike-test-" + [guid]::NewGuid().ToString("N") + ".db")
try {
    mvn "-Dtest=AuthenticationDatabaseIntegrationTest,CatalogDatabaseIntegrationTest,SalesDatabaseIntegrationTest,ReservationDatabaseIntegrationTest,AccessControlDatabaseIntegrationTest,RefundDatabaseIntegrationTest" test
} finally {
    Remove-Item Env:\TIKE_RUN_DB_INTEGRATION -ErrorAction SilentlyContinue
    if (Test-Path $env:TIKE_DB_PATH) { Remove-Item $env:TIKE_DB_PATH }
    Remove-Item Env:\TIKE_DB_PATH -ErrorAction SilentlyContinue
}
```

Les tests suppriment leurs données temporaires à la fin.
