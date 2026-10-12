INSERT INTO lieux (nom, adresse, ville, description)
SELECT 'Palais des Arts', 'Avenue de la Liberté', 'Antananarivo', 'Lieu de démonstration TIKE'
WHERE NOT EXISTS (SELECT 1 FROM lieux WHERE nom = 'Palais des Arts');

INSERT INTO salles (lieu_id, nom, capacite)
SELECT l.id, 'Salle Principale', 250
FROM lieux l
WHERE l.nom = 'Palais des Arts'
  AND NOT EXISTS (SELECT 1 FROM salles s WHERE s.lieu_id = l.id AND s.nom = 'Salle Principale');

INSERT INTO evenements (nom, description, type, salle_id, debut, fin, statut)
SELECT
    'Concert de démonstration TIKE',
    'Événement fictif destiné à tester l''application.',
    'CONCERT',
    s.id,
    CURRENT_TIMESTAMP + INTERVAL '10 days',
    CURRENT_TIMESTAMP + INTERVAL '10 days' + INTERVAL '3 hours',
    'PROGRAMME'
FROM salles s
JOIN lieux l ON l.id = s.lieu_id
WHERE l.nom = 'Palais des Arts'
  AND s.nom = 'Salle Principale'
  AND NOT EXISTS (SELECT 1 FROM evenements WHERE nom = 'Concert de démonstration TIKE');

INSERT INTO categories_billets (evenement_id, nom, prix, quantite_totale, description)
SELECT e.id, 'STANDARD', 20000.00, 150, 'Billet de démonstration'
FROM evenements e
WHERE e.nom = 'Concert de démonstration TIKE'
  AND NOT EXISTS (SELECT 1 FROM categories_billets c WHERE c.evenement_id = e.id AND c.nom = 'STANDARD');

INSERT INTO categories_billets (evenement_id, nom, prix, quantite_totale, description)
SELECT e.id, 'VIP', 50000.00, 25, 'Accès VIP de démonstration'
FROM evenements e
WHERE e.nom = 'Concert de démonstration TIKE'
  AND NOT EXISTS (SELECT 1 FROM categories_billets c WHERE c.evenement_id = e.id AND c.nom = 'VIP');

INSERT INTO clients (nom_complet, telephone, email)
SELECT 'Client Démo', '+261331234567', 'client.demo@example.com'
WHERE NOT EXISTS (SELECT 1 FROM clients WHERE telephone = '+261331234567');
