CREATE TABLE IF NOT EXISTS utilisateurs (
    id BIGSERIAL PRIMARY KEY,
    nom_complet VARCHAR(120) NOT NULL,
    nom_utilisateur VARCHAR(60) NOT NULL UNIQUE,
    mot_de_passe_hash TEXT NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'AGENT', 'CONTROLEUR')),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lieux (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(120) NOT NULL,
    adresse TEXT,
    ville VARCHAR(100),
    description TEXT
);

CREATE TABLE IF NOT EXISTS salles (
    id BIGSERIAL PRIMARY KEY,
    lieu_id BIGINT NOT NULL REFERENCES lieux(id),
    nom VARCHAR(120) NOT NULL,
    capacite INTEGER NOT NULL CHECK (capacite > 0),
    UNIQUE (lieu_id, nom)
);

CREATE TABLE IF NOT EXISTS evenements (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(180) NOT NULL,
    description TEXT,
    type VARCHAR(80) NOT NULL,
    salle_id BIGINT REFERENCES salles(id),
    debut TIMESTAMPTZ NOT NULL,
    fin TIMESTAMPTZ,
    ouverture_vente TIMESTAMPTZ,
    fermeture_vente TIMESTAMPTZ,
    statut VARCHAR(20) NOT NULL CHECK (statut IN ('BROUILLON', 'PROGRAMME', 'EN_VENTE', 'COMPLET', 'ANNULE', 'TERMINE')),
    cree_par BIGINT REFERENCES utilisateurs(id),
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (fin IS NULL OR fin > debut),
    CHECK (fermeture_vente IS NULL OR ouverture_vente IS NULL OR fermeture_vente > ouverture_vente)
);

CREATE TABLE IF NOT EXISTS categories_billets (
    id BIGSERIAL PRIMARY KEY,
    evenement_id BIGINT NOT NULL REFERENCES evenements(id),
    nom VARCHAR(80) NOT NULL,
    prix NUMERIC(12, 2) NOT NULL CHECK (prix >= 0),
    quantite_totale INTEGER NOT NULL CHECK (quantite_totale >= 0),
    description TEXT,
    UNIQUE (evenement_id, nom),
    UNIQUE (id, evenement_id)
);

CREATE TABLE IF NOT EXISTS clients (
    id BIGSERIAL PRIMARY KEY,
    nom_complet VARCHAR(160) NOT NULL,
    telephone VARCHAR(40),
    email VARCHAR(180),
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_telephone_unique
    ON clients (telephone) WHERE telephone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_clients_email ON clients (email) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS ventes (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT REFERENCES clients(id),
    evenement_id BIGINT NOT NULL REFERENCES evenements(id),
    vendeur_id BIGINT REFERENCES utilisateurs(id),
    numero_vente VARCHAR(40) NOT NULL UNIQUE,
    date_vente TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    montant_total NUMERIC(12, 2) NOT NULL CHECK (montant_total >= 0),
    statut VARCHAR(25) NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PAYEE', 'ANNULEE', 'REMBOURSEE')),
    UNIQUE (id, evenement_id)
);

CREATE TABLE IF NOT EXISTS vente_lignes (
    id BIGSERIAL PRIMARY KEY,
    vente_id BIGINT NOT NULL,
    evenement_id BIGINT NOT NULL,
    categorie_id BIGINT NOT NULL,
    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC(12, 2) NOT NULL CHECK (prix_unitaire >= 0),
    montant_ligne NUMERIC(12, 2) NOT NULL CHECK (montant_ligne >= 0),
    FOREIGN KEY (vente_id, evenement_id) REFERENCES ventes(id, evenement_id),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id),
    UNIQUE (vente_id, categorie_id)
);

CREATE TABLE IF NOT EXISTS paiements (
    id BIGSERIAL PRIMARY KEY,
    vente_id BIGINT NOT NULL REFERENCES ventes(id),
    montant NUMERIC(12, 2) NOT NULL CHECK (montant > 0),
    methode VARCHAR(30) NOT NULL CHECK (methode IN ('ESPECES', 'MOBILE_MONEY', 'CARTE', 'VIREMENT')),
    reference_paiement VARCHAR(120),
    statut VARCHAR(25) NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PAYE', 'ECHEC', 'REMBOURSE', 'PARTIELLEMENT_REMBOURSE')),
    effectue_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enregistre_par BIGINT REFERENCES utilisateurs(id)
);

CREATE TABLE IF NOT EXISTS remboursements (
    id BIGSERIAL PRIMARY KEY,
    vente_id BIGINT NOT NULL UNIQUE REFERENCES ventes(id),
    numero_remboursement VARCHAR(40) NOT NULL UNIQUE,
    montant NUMERIC(12, 2) NOT NULL CHECK (montant > 0),
    motif VARCHAR(500) NOT NULL,
    effectue_par BIGINT NOT NULL REFERENCES utilisateurs(id),
    effectue_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billets (
    id BIGSERIAL PRIMARY KEY,
    vente_id BIGINT NOT NULL,
    evenement_id BIGINT NOT NULL,
    categorie_id BIGINT NOT NULL,
    code_unique UUID NOT NULL UNIQUE,
    statut VARCHAR(20) NOT NULL CHECK (statut IN ('VALIDE', 'UTILISE', 'ANNULE', 'REMBOURSE')),
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    utilise_le TIMESTAMPTZ,
    controle_par BIGINT REFERENCES utilisateurs(id),
    FOREIGN KEY (vente_id, evenement_id) REFERENCES ventes(id, evenement_id),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id)
);

CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    numero_reservation VARCHAR(40) NOT NULL UNIQUE,
    evenement_id BIGINT NOT NULL REFERENCES evenements(id),
    client_id BIGINT REFERENCES clients(id),
    cree_par BIGINT REFERENCES utilisateurs(id),
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_le TIMESTAMPTZ NOT NULL,
    statut VARCHAR(20) NOT NULL CHECK (statut IN ('EN_ATTENTE', 'CONFIRMEE', 'EXPIREE', 'ANNULEE')),
    vente_id BIGINT UNIQUE REFERENCES ventes(id),
    CHECK (expire_le > cree_le),
    CHECK ((statut = 'CONFIRMEE' AND vente_id IS NOT NULL)
        OR (statut <> 'CONFIRMEE' AND vente_id IS NULL))
);

CREATE TABLE IF NOT EXISTS reservation_lignes (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    evenement_id BIGINT NOT NULL,
    categorie_id BIGINT NOT NULL,
    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC(12, 2) NOT NULL CHECK (prix_unitaire >= 0),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id),
    UNIQUE (reservation_id, categorie_id)
);

CREATE TABLE IF NOT EXISTS controles_acces (
    id BIGSERIAL PRIMARY KEY,
    billet_id BIGINT REFERENCES billets(id),
    controleur_id BIGINT REFERENCES utilisateurs(id),
    evenement_id BIGINT NOT NULL REFERENCES evenements(id),
    resultat VARCHAR(30) NOT NULL CHECK (resultat IN ('VALIDE', 'DEJA_UTILISE', 'NON_TROUVE', 'NON_PAYE', 'ANNULE', 'MAUVAIS_EVENEMENT')),
    controle_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

CREATE TABLE IF NOT EXISTS journal_activites (
    id BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT REFERENCES utilisateurs(id),
    action VARCHAR(100) NOT NULL,
    type_objet VARCHAR(80),
    objet_id BIGINT,
    details TEXT,
    cree_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evenements_debut ON evenements (debut);
CREATE INDEX IF NOT EXISTS idx_categories_evenement ON categories_billets (evenement_id);
CREATE INDEX IF NOT EXISTS idx_ventes_evenement_date ON ventes (evenement_id, date_vente);
CREATE INDEX IF NOT EXISTS idx_paiements_vente ON paiements (vente_id);
CREATE INDEX IF NOT EXISTS idx_billets_vente ON billets (vente_id);
CREATE INDEX IF NOT EXISTS idx_billets_evenement_statut ON billets (evenement_id, statut);
CREATE INDEX IF NOT EXISTS idx_reservations_expiration
    ON reservations (evenement_id, expire_le) WHERE statut = 'EN_ATTENTE';
CREATE INDEX IF NOT EXISTS idx_reservation_lignes_categorie ON reservation_lignes (categorie_id);
CREATE INDEX IF NOT EXISTS idx_controles_evenement_date ON controles_acces (evenement_id, controle_le);
CREATE INDEX IF NOT EXISTS idx_journal_date ON journal_activites (cree_le);

-- Le stock disponible exclut les billets vendus et les réservations encore actives.
CREATE OR REPLACE VIEW stock_categories AS
SELECT
    c.id AS categorie_id,
    c.evenement_id,
    c.nom,
    c.quantite_totale,
    GREATEST(
        c.quantite_totale
        - COUNT(b.id) FILTER (WHERE v.statut = 'PAYEE' AND b.statut NOT IN ('ANNULE', 'REMBOURSE'))
        - COALESCE((
            SELECT SUM(rl.quantite)
            FROM reservation_lignes rl
            JOIN reservations r ON r.id = rl.reservation_id
            WHERE rl.categorie_id = c.id
              AND r.statut = 'EN_ATTENTE'
              AND r.expire_le > CURRENT_TIMESTAMP
        ), 0),
        0
    )::INTEGER AS quantite_disponible
FROM categories_billets c
LEFT JOIN billets b ON b.categorie_id = c.id AND b.evenement_id = c.evenement_id
LEFT JOIN ventes v ON v.id = b.vente_id AND v.evenement_id = b.evenement_id
GROUP BY c.id, c.evenement_id, c.nom, c.quantite_totale;
