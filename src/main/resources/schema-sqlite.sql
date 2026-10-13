CREATE TABLE IF NOT EXISTS utilisateurs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom_complet TEXT NOT NULL CHECK (length(nom_complet) <= 120),
    nom_utilisateur TEXT NOT NULL UNIQUE CHECK (length(nom_utilisateur) <= 60),
    mot_de_passe_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'AGENT', 'CONTROLEUR') AND length(role) <= 20),
    actif INTEGER NOT NULL DEFAULT 1 CHECK (actif IN (0, 1)),
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS lieux (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL CHECK (length(nom) <= 120),
    adresse TEXT,
    ville TEXT CHECK (ville IS NULL OR length(ville) <= 100),
    description TEXT
);

CREATE TABLE IF NOT EXISTS salles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lieu_id INTEGER NOT NULL REFERENCES lieux(id),
    nom TEXT NOT NULL CHECK (length(nom) <= 120),
    capacite INTEGER NOT NULL CHECK (capacite > 0),
    UNIQUE (lieu_id, nom)
);

CREATE TABLE IF NOT EXISTS evenements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL CHECK (length(nom) <= 180),
    description TEXT,
    type TEXT NOT NULL CHECK (length(type) <= 80),
    salle_id INTEGER REFERENCES salles(id),
    debut TEXT NOT NULL,
    fin TEXT,
    ouverture_vente TEXT,
    fermeture_vente TEXT,
    statut TEXT NOT NULL CHECK (statut IN ('BROUILLON', 'PROGRAMME', 'EN_VENTE', 'COMPLET', 'ANNULE', 'TERMINE')),
    cree_par INTEGER REFERENCES utilisateurs(id),
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    CHECK (fin IS NULL OR fin > debut),
    CHECK (fermeture_vente IS NULL OR ouverture_vente IS NULL OR fermeture_vente > ouverture_vente)
);

CREATE TABLE IF NOT EXISTS categories_billets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    evenement_id INTEGER NOT NULL REFERENCES evenements(id),
    nom TEXT NOT NULL CHECK (length(nom) <= 80),
    prix NUMERIC NOT NULL CHECK (prix >= 0),
    quantite_totale INTEGER NOT NULL CHECK (quantite_totale >= 0),
    description TEXT,
    UNIQUE (evenement_id, nom),
    UNIQUE (id, evenement_id)
);

CREATE TABLE IF NOT EXISTS clients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom_complet TEXT NOT NULL CHECK (length(nom_complet) <= 160),
    telephone TEXT CHECK (telephone IS NULL OR length(telephone) <= 40),
    email TEXT CHECK (email IS NULL OR length(email) <= 180),
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_telephone_unique
    ON clients (telephone) WHERE telephone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_clients_email ON clients (email) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS ventes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER REFERENCES clients(id),
    evenement_id INTEGER NOT NULL REFERENCES evenements(id),
    vendeur_id INTEGER REFERENCES utilisateurs(id),
    numero_vente TEXT NOT NULL UNIQUE CHECK (length(numero_vente) <= 40),
    date_vente TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    montant_total NUMERIC NOT NULL CHECK (montant_total >= 0),
    statut TEXT NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PAYEE', 'ANNULEE', 'REMBOURSEE')
                                AND length(statut) <= 25),
    UNIQUE (id, evenement_id)
);

CREATE TABLE IF NOT EXISTS vente_lignes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vente_id INTEGER NOT NULL,
    evenement_id INTEGER NOT NULL,
    categorie_id INTEGER NOT NULL,
    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC NOT NULL CHECK (prix_unitaire >= 0),
    montant_ligne NUMERIC NOT NULL CHECK (montant_ligne >= 0),
    FOREIGN KEY (vente_id, evenement_id) REFERENCES ventes(id, evenement_id),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id),
    UNIQUE (vente_id, categorie_id)
);

CREATE TABLE IF NOT EXISTS paiements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vente_id INTEGER NOT NULL REFERENCES ventes(id),
    montant NUMERIC NOT NULL CHECK (montant > 0),
    methode TEXT NOT NULL CHECK (methode IN ('ESPECES', 'MOBILE_MONEY', 'CARTE', 'VIREMENT')
                                 AND length(methode) <= 30),
    reference_paiement TEXT CHECK (reference_paiement IS NULL OR length(reference_paiement) <= 120),
    statut TEXT NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PAYE', 'ECHEC', 'REMBOURSE', 'PARTIELLEMENT_REMBOURSE')
                                AND length(statut) <= 25),
    effectue_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    enregistre_par INTEGER REFERENCES utilisateurs(id)
);

CREATE TABLE IF NOT EXISTS remboursements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vente_id INTEGER NOT NULL UNIQUE REFERENCES ventes(id),
    numero_remboursement TEXT NOT NULL UNIQUE CHECK (length(numero_remboursement) <= 40),
    montant NUMERIC NOT NULL CHECK (montant > 0),
    motif TEXT NOT NULL CHECK (length(motif) <= 500),
    effectue_par INTEGER NOT NULL REFERENCES utilisateurs(id),
    effectue_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS billets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vente_id INTEGER NOT NULL,
    evenement_id INTEGER NOT NULL,
    categorie_id INTEGER NOT NULL,
    code_unique TEXT NOT NULL UNIQUE CHECK (length(code_unique) = 36),
    statut TEXT NOT NULL CHECK (statut IN ('VALIDE', 'UTILISE', 'ANNULE', 'REMBOURSE')
                                AND length(statut) <= 20),
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    utilise_le TEXT,
    controle_par INTEGER REFERENCES utilisateurs(id),
    FOREIGN KEY (vente_id, evenement_id) REFERENCES ventes(id, evenement_id),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id)
);

CREATE TABLE IF NOT EXISTS reservations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_reservation TEXT NOT NULL UNIQUE CHECK (length(numero_reservation) <= 40),
    evenement_id INTEGER NOT NULL REFERENCES evenements(id),
    client_id INTEGER REFERENCES clients(id),
    cree_par INTEGER REFERENCES utilisateurs(id),
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    expire_le TEXT NOT NULL,
    statut TEXT NOT NULL CHECK (statut IN ('EN_ATTENTE', 'CONFIRMEE', 'EXPIREE', 'ANNULEE')
                                AND length(statut) <= 20),
    vente_id INTEGER UNIQUE REFERENCES ventes(id),
    CHECK (expire_le > cree_le),
    CHECK ((statut = 'CONFIRMEE' AND vente_id IS NOT NULL)
        OR (statut <> 'CONFIRMEE' AND vente_id IS NULL))
);

CREATE TABLE IF NOT EXISTS reservation_lignes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reservation_id INTEGER NOT NULL REFERENCES reservations(id),
    evenement_id INTEGER NOT NULL,
    categorie_id INTEGER NOT NULL,
    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC NOT NULL CHECK (prix_unitaire >= 0),
    FOREIGN KEY (categorie_id, evenement_id) REFERENCES categories_billets(id, evenement_id),
    UNIQUE (reservation_id, categorie_id)
);

CREATE TABLE IF NOT EXISTS controles_acces (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    billet_id INTEGER REFERENCES billets(id),
    controleur_id INTEGER REFERENCES utilisateurs(id),
    evenement_id INTEGER NOT NULL REFERENCES evenements(id),
    resultat TEXT NOT NULL CHECK (resultat IN ('VALIDE', 'DEJA_UTILISE', 'NON_TROUVE', 'NON_PAYE', 'ANNULE', 'MAUVAIS_EVENEMENT')
                                  AND length(resultat) <= 30),
    controle_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    details TEXT
);

CREATE TABLE IF NOT EXISTS journal_activites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    utilisateur_id INTEGER REFERENCES utilisateurs(id),
    action TEXT NOT NULL CHECK (length(action) <= 100),
    type_objet TEXT CHECK (type_objet IS NULL OR length(type_objet) <= 80),
    objet_id INTEGER,
    details TEXT,
    cree_le TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
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

CREATE VIEW IF NOT EXISTS stock_categories AS
SELECT c.id AS categorie_id,
       c.evenement_id,
       c.nom,
       c.quantite_totale,
       MAX(c.quantite_totale
           - SUM(CASE WHEN v.statut = 'PAYEE' AND b.statut NOT IN ('ANNULE', 'REMBOURSE')
                      THEN 1 ELSE 0 END)
           - COALESCE((
               SELECT SUM(rl.quantite)
               FROM reservation_lignes rl
               JOIN reservations r ON r.id = rl.reservation_id
               WHERE rl.categorie_id = c.id
                 AND r.statut = 'EN_ATTENTE'
                 AND r.expire_le > datetime('now', 'localtime')
           ), 0), 0) AS quantite_disponible
FROM categories_billets c
LEFT JOIN billets b ON b.categorie_id = c.id AND b.evenement_id = c.evenement_id
LEFT JOIN ventes v ON v.id = b.vente_id AND v.evenement_id = b.evenement_id
GROUP BY c.id, c.evenement_id, c.nom, c.quantite_totale;
