-- Donnees de demonstration TIKE pour SQLite.
-- Appliquer schema-sqlite.sql avant ce script. Les comptes crees sont desactives.
BEGIN IMMEDIATE;

DROP TABLE IF EXISTS temp.demo_numbers;
DROP TABLE IF EXISTS temp.demo_guard;
DROP TABLE IF EXISTS temp.demo_admin;
DROP TABLE IF EXISTS temp.demo_admin_actor;
DROP TABLE IF EXISTS temp.demo_venues;
DROP TABLE IF EXISTS temp.demo_rooms;
DROP TABLE IF EXISTS temp.demo_events;
DROP TABLE IF EXISTS temp.demo_categories;
DROP TABLE IF EXISTS temp.demo_clients;
DROP TABLE IF EXISTS temp.demo_sales;
DROP TABLE IF EXISTS temp.demo_tickets;
DROP TABLE IF EXISTS temp.demo_reservations;

CREATE TEMP TABLE demo_numbers AS
WITH RECURSIVE numbers(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 20
)
SELECT n FROM numbers;

CREATE TEMP TABLE demo_admin AS
SELECT id FROM utilisateurs
WHERE role = 'ADMIN' AND actif = 1
ORDER BY id
LIMIT 1;

CREATE TEMP TABLE demo_guard (valid INTEGER NOT NULL CHECK (valid = 1));
INSERT INTO demo_guard
SELECT CASE WHEN EXISTS (SELECT 1 FROM demo_admin) THEN 1 ELSE 0 END;

CREATE TEMP TABLE demo_admin_actor AS
SELECT id FROM demo_admin;

INSERT INTO utilisateurs (nom_complet, nom_utilisateur, mot_de_passe_hash, role, actif)
SELECT
    CASE n % 3
        WHEN 1 THEN 'Mpandrindra santionany ' || printf('%02d', n)
        WHEN 2 THEN 'Mpivarotra santionany ' || printf('%02d', n)
        ELSE 'Mpifehy fidirana santionany ' || printf('%02d', n)
    END,
    'demo.mg.' || printf('%02d', n),
    '{DEMO_ACCOUNT_PASSWORD_RESET_REQUIRED}',
    CASE n % 3 WHEN 1 THEN 'ADMIN' WHEN 2 THEN 'AGENT' ELSE 'CONTROLEUR' END,
    0
FROM demo_numbers
WHERE n < 20
ON CONFLICT (nom_utilisateur) DO UPDATE SET
    nom_complet = excluded.nom_complet,
    mot_de_passe_hash = excluded.mot_de_passe_hash,
    role = excluded.role,
    actif = 0;

INSERT INTO lieux (nom, adresse, ville, description)
SELECT
    'DEMO-MG-LIEU-' || printf('%02d', n),
    'Lalana ' || CASE n
        WHEN 1 THEN 'Analakely' WHEN 2 THEN 'Bazary Be' WHEN 3 THEN 'Isaha'
        WHEN 4 THEN 'Ampasika' WHEN 5 THEN 'Anketa' WHEN 6 THEN 'Tsarasaotra'
        WHEN 7 THEN 'Tanambao' WHEN 8 THEN 'Nosy Kely' WHEN 9 THEN 'Ampilao'
        WHEN 10 THEN 'Ambodisatrana' WHEN 11 THEN 'Ivony' WHEN 12 THEN 'Mahazoarivo'
        WHEN 13 THEN 'Manambotra' WHEN 14 THEN 'Andranofotsy' WHEN 15 THEN 'Ambohimena'
        WHEN 16 THEN 'Ambohitrarivo' WHEN 17 THEN 'Ambinagny' WHEN 18 THEN 'Ampasina'
        WHEN 19 THEN 'Hell-Ville' ELSE 'Marovato'
    END,
    CASE n
        WHEN 1 THEN 'Antananarivo' WHEN 2 THEN 'Toamasina' WHEN 3 THEN 'Fianarantsoa'
        WHEN 4 THEN 'Mahajanga' WHEN 5 THEN 'Toliara' WHEN 6 THEN 'Antsirabe'
        WHEN 7 THEN 'Antsiranana' WHEN 8 THEN 'Morondava' WHEN 9 THEN 'Manakara'
        WHEN 10 THEN 'Sambava' WHEN 11 THEN 'Ambositra' WHEN 12 THEN 'Ihosy'
        WHEN 13 THEN 'Farafangana' WHEN 14 THEN 'Maroantsetra' WHEN 15 THEN 'Ambanja'
        WHEN 16 THEN 'Miarinarivo' WHEN 17 THEN 'Antalaha' WHEN 18 THEN 'Fenoarivo Atsinanana'
        WHEN 19 THEN 'Nosy Be' ELSE 'Ambovombe'
    END,
    'Toerana santionany ho an''ny hetsika ara-kolontsaina.'
FROM demo_numbers
WHERE NOT EXISTS (
    SELECT 1 FROM lieux l WHERE l.nom = 'DEMO-MG-LIEU-' || printf('%02d', demo_numbers.n)
);

CREATE TEMP TABLE demo_venues AS
SELECT n.n, l.id AS venue_id, l.nom AS venue_name
FROM demo_numbers n
JOIN lieux l ON l.nom = 'DEMO-MG-LIEU-' || printf('%02d', n.n);

INSERT INTO salles (lieu_id, nom, capacite)
SELECT venue_id, 'Efitrano lehibe', 500
FROM demo_venues
WHERE NOT EXISTS (
    SELECT 1 FROM salles s
    WHERE s.lieu_id = demo_venues.venue_id AND s.nom = 'Efitrano lehibe'
);

CREATE TEMP TABLE demo_rooms AS
SELECT v.n, v.venue_id, s.id AS room_id
FROM demo_venues v
JOIN salles s ON s.lieu_id = v.venue_id AND s.nom = 'Efitrano lehibe';

INSERT INTO evenements (
    nom, description, type, salle_id, debut, fin, ouverture_vente, fermeture_vente, statut, cree_par
)
SELECT
    'DEMO-MG-HETSIKA-' || printf('%02d', r.n) || ' - ' || CASE r.n
        WHEN 1 THEN 'Fetin''ny hira Malagasy'
        WHEN 2 THEN 'Hira sy dihy amoron-dranomasina'
        WHEN 3 THEN 'Fampisehoana vakodrazana'
        WHEN 4 THEN 'Fetin''ny mozika sy ny zavakanto'
        WHEN 5 THEN 'Seho an-tsehatra Malagasy'
        WHEN 6 THEN 'Fifaninanana hira nentim-paharazana'
        WHEN 7 THEN 'Fetin''ny valiha'
        WHEN 8 THEN 'Hetsika ho an''ny tanora'
        WHEN 9 THEN 'Fampisehoana dihy'
        WHEN 10 THEN 'Fetin''ny kolontsaina'
        WHEN 11 THEN 'Seho mozika an-tanàna'
        WHEN 12 THEN 'Fampisehoana tantara'
        WHEN 13 THEN 'Fetin''ny zavakanto'
        WHEN 14 THEN 'Hira sy kabary'
        WHEN 15 THEN 'Seho an-kalamanjana'
        WHEN 16 THEN 'Fetin''ny ankizy'
        WHEN 17 THEN 'Fampisehoana hira sy mozika'
        WHEN 18 THEN 'Fifanentanana ara-kolontsaina'
        WHEN 19 THEN 'Hetsika fankalazana'
        ELSE 'Fetin''ny fiaraha-monina'
    END,
    'Hetsika santionany: mozika, dihy ary kolontsaina Malagasy.',
    CASE WHEN r.n % 2 = 0 THEN 'MOZIKA' ELSE 'KOLONTSAINA' END,
    r.room_id,
    datetime('now', 'localtime', '+' || (10 + r.n) || ' days'),
    datetime('now', 'localtime', '+' || (10 + r.n) || ' days', '+3 hours'),
    datetime('now', 'localtime', '-1 day'),
    datetime('now', 'localtime', '+' || (9 + r.n) || ' days', '+23 hours'),
    'EN_VENTE',
    actor.id
FROM demo_rooms r
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM evenements e
    WHERE e.nom LIKE 'DEMO-MG-HETSIKA-' || printf('%02d', r.n) || ' - %'
);

CREATE TEMP TABLE demo_events AS
SELECT r.n, r.room_id, e.id AS event_id
FROM demo_rooms r
JOIN evenements e ON e.nom LIKE 'DEMO-MG-HETSIKA-' || printf('%02d', r.n) || ' - %';

INSERT INTO categories_billets (evenement_id, nom, prix, quantite_totale, description)
SELECT
    event_id,
    CASE n % 3 WHEN 0 THEN 'ECONOMIQUE' WHEN 1 THEN 'STANDARD' ELSE 'VIP' END,
    5000 + n * 2500,
    500,
    'Sokajy tapakila santionany; vidiny amin''ny Ariary.'
FROM demo_events
WHERE NOT EXISTS (
    SELECT 1 FROM categories_billets c
    WHERE c.evenement_id = demo_events.event_id
      AND c.nom = CASE demo_events.n % 3
          WHEN 0 THEN 'ECONOMIQUE' WHEN 1 THEN 'STANDARD' ELSE 'VIP' END
);

CREATE TEMP TABLE demo_categories AS
SELECT e.n, e.event_id, c.id AS category_id, c.prix
FROM demo_events e
JOIN categories_billets c ON c.evenement_id = e.event_id
WHERE c.nom = CASE e.n % 3 WHEN 0 THEN 'ECONOMIQUE' WHEN 1 THEN 'STANDARD' ELSE 'VIP' END;

INSERT INTO clients (nom_complet, telephone, email)
SELECT
    CASE n
        WHEN 1 THEN 'Rasoa Rakoto' WHEN 2 THEN 'Andry Randria'
        WHEN 3 THEN 'Soa Razafindrakoto' WHEN 4 THEN 'Hery Raveloson'
        WHEN 5 THEN 'Fara Andrianina' WHEN 6 THEN 'Tiana Rakotomalala'
        WHEN 7 THEN 'Mamy Randrianarisoa' WHEN 8 THEN 'Lova Rasoanaivo'
        WHEN 9 THEN 'Noro Rabarijaona' WHEN 10 THEN 'Solofo Andriamihaja'
        WHEN 11 THEN 'Voahirana Rakotobe' WHEN 12 THEN 'Hasina Ravelomanana'
        WHEN 13 THEN 'Miora Andriatsiferana' WHEN 14 THEN 'Toky Rakotondrazaka'
        WHEN 15 THEN 'Faly Randriamampionona' WHEN 16 THEN 'Hanitra Rasoazananera'
        WHEN 17 THEN 'Ony Randriamanantena' WHEN 18 THEN 'Lisy Rakotoniaina'
        WHEN 19 THEN 'Njaka Andriambololona' ELSE 'Malala Razafindrabe'
    END,
    '+26132' || printf('%07d', 1000000 + n),
    'mpanjifa' || printf('%02d', n) || '@ohatra.mg'
FROM demo_numbers
WHERE NOT EXISTS (
    SELECT 1 FROM clients c
    WHERE c.telephone = '+26132' || printf('%07d', demo_numbers.n + 1000000)
);

CREATE TEMP TABLE demo_clients AS
SELECT CAST(substr(email, 9, 2) AS INTEGER) AS n, id AS client_id
FROM clients
WHERE email LIKE 'mpanjifa__@ohatra.mg';

CREATE TEMP TABLE demo_users AS
SELECT CAST(substr(nom_utilisateur, 9, 2) AS INTEGER) AS n, id AS user_id, role
FROM utilisateurs
WHERE nom_utilisateur LIKE 'demo.mg.__';

INSERT INTO ventes (client_id, evenement_id, vendeur_id, numero_vente, date_vente, montant_total, statut)
SELECT
    buyer.client_id,
    c.event_id,
    actor.id,
    'DEMO-MG-V-' || printf('%02d', c.n),
    datetime('now', 'localtime', '-' || c.n || ' hours'),
    c.prix,
    CASE WHEN c.n > 15 THEN 'REMBOURSEE' ELSE 'PAYEE' END
FROM demo_categories c
JOIN demo_clients buyer ON buyer.n = c.n
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM ventes v WHERE v.numero_vente = 'DEMO-MG-V-' || printf('%02d', c.n)
);

CREATE TEMP TABLE demo_sales AS
SELECT c.n, c.event_id, c.category_id, c.prix, v.id AS sale_id, v.numero_vente
FROM demo_categories c
JOIN ventes v ON v.numero_vente = 'DEMO-MG-V-' || printf('%02d', c.n);

INSERT INTO vente_lignes (vente_id, evenement_id, categorie_id, quantite, prix_unitaire, montant_ligne)
SELECT sale_id, event_id, category_id, 1, prix, prix
FROM demo_sales
WHERE NOT EXISTS (
    SELECT 1 FROM vente_lignes vl
    WHERE vl.vente_id = demo_sales.sale_id AND vl.categorie_id = demo_sales.category_id
);

INSERT INTO paiements (vente_id, montant, methode, reference_paiement, statut, enregistre_par)
SELECT
    s.sale_id,
    s.prix,
    CASE s.n % 4 WHEN 0 THEN 'MOBILE_MONEY' WHEN 1 THEN 'ESPECES'
        WHEN 2 THEN 'CARTE' ELSE 'VIREMENT' END,
    'DEMO-MG-PAY-' || printf('%02d', s.n),
    CASE WHEN s.n > 15 THEN 'REMBOURSE' ELSE 'PAYE' END,
    actor.id
FROM demo_sales s
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM paiements p WHERE p.reference_paiement = 'DEMO-MG-PAY-' || printf('%02d', s.n)
);

INSERT INTO billets (vente_id, evenement_id, categorie_id, code_unique, statut, utilise_le, controle_par)
SELECT
    s.sale_id,
    s.event_id,
    s.category_id,
    lower(printf('%08x-0000-4000-8000-%012x', s.n, s.n)),
    CASE WHEN s.n > 15 THEN 'REMBOURSE' WHEN s.n % 5 = 0 THEN 'UTILISE' ELSE 'VALIDE' END,
    CASE WHEN s.n <= 15 AND s.n % 5 = 0
        THEN datetime('now', 'localtime', '-10 minutes') END,
    CASE WHEN s.n <= 15 AND s.n % 5 = 0 THEN actor.id END
FROM demo_sales s
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM billets b
    WHERE b.code_unique = lower(printf('%08x-0000-4000-8000-%012x', s.n, s.n))
);

CREATE TEMP TABLE demo_tickets AS
SELECT s.n, s.event_id, s.sale_id, b.id AS ticket_id, b.code_unique, b.statut
FROM demo_sales s
JOIN billets b ON b.vente_id = s.sale_id;

INSERT INTO remboursements (vente_id, numero_remboursement, montant, motif, effectue_par)
SELECT
    s.sale_id,
    'DEMO-MG-R-' || printf('%02d', s.n),
    s.prix,
    'Antony santionany voarakitra rehefa vita ivelan''ny TIKE.',
    actor.id
FROM demo_sales s
CROSS JOIN demo_admin_actor actor
WHERE s.n > 15
  AND NOT EXISTS (SELECT 1 FROM remboursements r WHERE r.vente_id = s.sale_id);

INSERT INTO reservations (numero_reservation, evenement_id, client_id, cree_par, expire_le, statut)
SELECT
    'DEMO-MG-RES-' || printf('%02d', c.n),
    c.event_id,
    buyer.client_id,
    actor.id,
    datetime('now', 'localtime', '+2 days'),
    'EN_ATTENTE'
FROM demo_categories c
JOIN demo_clients buyer ON buyer.n = c.n
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM reservations r
    WHERE r.numero_reservation = 'DEMO-MG-RES-' || printf('%02d', c.n)
);

CREATE TEMP TABLE demo_reservations AS
SELECT c.n, c.event_id, c.category_id, c.prix, r.id AS reservation_id
FROM demo_categories c
JOIN reservations r ON r.numero_reservation = 'DEMO-MG-RES-' || printf('%02d', c.n);

INSERT INTO reservation_lignes (reservation_id, evenement_id, categorie_id, quantite, prix_unitaire)
SELECT reservation_id, event_id, category_id, 1, prix
FROM demo_reservations
WHERE NOT EXISTS (
    SELECT 1 FROM reservation_lignes rl
    WHERE rl.reservation_id = demo_reservations.reservation_id
      AND rl.categorie_id = demo_reservations.category_id
);

INSERT INTO controles_acces (billet_id, controleur_id, evenement_id, resultat, details)
SELECT
    t.ticket_id,
    actor.id,
    t.event_id,
    CASE
        WHEN t.statut = 'REMBOURSE' THEN 'ANNULE'
        WHEN t.statut = 'UTILISE' THEN 'DEJA_UTILISE'
        ELSE 'VALIDE'
    END,
    'DEMO-MG-CONTROLE-' || printf('%02d', t.n)
FROM demo_tickets t
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM controles_acces ca
    WHERE ca.details = 'DEMO-MG-CONTROLE-' || printf('%02d', t.n)
);

INSERT INTO journal_activites (utilisateur_id, action, type_objet, objet_id, details)
SELECT
    actor.id,
    CASE
        WHEN s.n > 15 THEN 'REMBOURSEMENT_DEMO'
        WHEN s.n % 5 = 0 THEN 'CONTROLE_BILLET_DEMO'
        ELSE 'VENTE_DEMO'
    END,
    'VENTE',
    s.sale_id,
    'DEMO-MG-JOURNAL-' || printf('%02d', s.n)
        || ' - Firaketana santionany ho an''ny fitsapana.'
FROM demo_sales s
CROSS JOIN demo_admin_actor actor
WHERE NOT EXISTS (
    SELECT 1 FROM journal_activites j
    WHERE j.details LIKE 'DEMO-MG-JOURNAL-' || printf('%02d', s.n) || '%'
);

DROP TABLE temp.demo_numbers;
DROP TABLE temp.demo_guard;
DROP TABLE temp.demo_admin;
DROP TABLE temp.demo_admin_actor;
DROP TABLE temp.demo_venues;
DROP TABLE temp.demo_rooms;
DROP TABLE temp.demo_events;
DROP TABLE temp.demo_categories;
DROP TABLE temp.demo_clients;
DROP TABLE temp.demo_users;
DROP TABLE temp.demo_sales;
DROP TABLE temp.demo_tickets;
DROP TABLE temp.demo_reservations;

COMMIT;
