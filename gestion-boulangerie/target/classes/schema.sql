-- ============================================================
--  SCHEMA SQL — Gestion Boulangerie v1.0
--  Base : MySQL 8.x
--  Encodage : UTF-8
-- ============================================================

CREATE DATABASE IF NOT EXISTS boulangerie
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE boulangerie;

-- ─────────────────────────────────────────────
--  SÉCURITÉ & UTILISATEURS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS role (
    id          VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    nom         VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS utilisateur (
    id              VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    login           VARCHAR(50)  NOT NULL UNIQUE,
    mot_de_passe    VARCHAR(255) NOT NULL,   -- BCrypt hash
    nom_complet     VARCHAR(100) NOT NULL,
    telephone       VARCHAR(20),
    email           VARCHAR(100),
    role_id         VARCHAR(36)  NOT NULL,
    actif           TINYINT(1)   NOT NULL DEFAULT 1,
    date_creation   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    derniere_connexion DATETIME,
    FOREIGN KEY (role_id) REFERENCES role(id)
);

CREATE TABLE IF NOT EXISTS permission (
    id          VARCHAR(36) NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id       VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(id),
    FOREIGN KEY (permission_id) REFERENCES permission(id)
);

-- ─────────────────────────────────────────────
--  CATALOGUE PRODUITS & TARIFS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS famille (
    id  VARCHAR(36) NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    nom VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS produit (
    id              VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    code            VARCHAR(20)    NOT NULL UNIQUE,
    libelle         VARCHAR(200)   NOT NULL,
    famille_id      VARCHAR(36),
    unite           VARCHAR(20)    NOT NULL DEFAULT 'Pièce',
    statut          ENUM('Actif','Inactif') NOT NULL DEFAULT 'Actif',
    seuil_alerte    INT            NOT NULL DEFAULT 0,
    description     TEXT,
    date_creation   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (famille_id) REFERENCES famille(id)
);

CREATE TABLE IF NOT EXISTS tarif (
    id          VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    produit_id  VARCHAR(36)    NOT NULL,
    type_tarif  ENUM('Standard','Externe','Interne','Carrefour','Specifique') NOT NULL,
    montant     DECIMAL(15,2)  NOT NULL,
    date_debut  DATE           NOT NULL,
    date_fin    DATE,
    statut      ENUM('Actif','Inactif') NOT NULL DEFAULT 'Actif',
    FOREIGN KEY (produit_id) REFERENCES produit(id)
);

CREATE TABLE IF NOT EXISTS remise_exceptionnelle (
    id               VARCHAR(36)   NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    produit_id       VARCHAR(36)   NOT NULL,
    motif            VARCHAR(200),
    prix_normal      DECIMAL(15,2) NOT NULL,
    prix_accorde     DECIMAL(15,2) NOT NULL,
    quantite_min     INT           NOT NULL DEFAULT 1,
    date_debut       DATE          NOT NULL,
    date_fin         DATE          NOT NULL,
    actif            TINYINT(1)    NOT NULL DEFAULT 1,
    cree_par         VARCHAR(36),
    date_creation    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produit_id) REFERENCES produit(id),
    FOREIGN KEY (cree_par) REFERENCES utilisateur(id)
);

-- ─────────────────────────────────────────────
--  CLIENTS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categorie_client (
    id  VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    nom VARCHAR(50)  NOT NULL UNIQUE   -- Externe, Interne, Carrefour
);

CREATE TABLE IF NOT EXISTS client (
    id                 VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    code               VARCHAR(20)    NOT NULL UNIQUE,
    nom                VARCHAR(150)   NOT NULL,
    quartier           VARCHAR(100),
    ville              VARCHAR(100),
    telephone          VARCHAR(20),
    email              VARCHAR(100),
    categorie_id       VARCHAR(36)    NOT NULL,
    est_anonyme        TINYINT(1)     NOT NULL DEFAULT 0,
    type_client        ENUM('Nominatif','Anonyme') NOT NULL DEFAULT 'Nominatif',
    livreur_rattache   VARCHAR(36),    -- FK utilisateur livreur
    delai_paiement     INT            NOT NULL DEFAULT 30,
    plafond_credit     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    solde_precedent    DECIMAL(15,2)  NOT NULL DEFAULT 0,
    solde_actuel       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    statut             ENUM('Actif','Bloqué','Inactif') NOT NULL DEFAULT 'Actif',
    notes              TEXT,
    date_creation      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (categorie_id) REFERENCES categorie_client(id),
    FOREIGN KEY (livreur_rattache) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS tarif_client (
    id          VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    client_id   VARCHAR(36)    NOT NULL,
    produit_id  VARCHAR(36)    NOT NULL,
    prix        DECIMAL(15,2)  NOT NULL,
    date_debut  DATE           NOT NULL,
    date_fin    DATE,
    actif       TINYINT(1)     NOT NULL DEFAULT 1,
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (produit_id) REFERENCES produit(id)
);

CREATE TABLE IF NOT EXISTS blocage (
    id               VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    client_id        VARCHAR(36)    NOT NULL,
    date_blocage     DATE           NOT NULL,
    motif            VARCHAR(300),
    montant_dette    DECIMAL(15,2),
    statut           ENUM('Actif','Levé') NOT NULL DEFAULT 'Actif',
    leve_par         VARCHAR(36),
    date_levee       DATETIME,
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (leve_par) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS autorisation_deblocage (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    client_id           VARCHAR(36)    NOT NULL,
    manager_id          VARCHAR(36)    NOT NULL,
    date_autorisation   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motif               VARCHAR(300),
    engagement_client   TEXT,
    montant_autorise    DECIMAL(15,2),
    duree_validite      DATE,
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (manager_id) REFERENCES utilisateur(id)
);

-- ─────────────────────────────────────────────
--  SORTIES & RETOURS (Fiches journalières)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fiche_journaliere (
    id              VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero          VARCHAR(20)  NOT NULL UNIQUE,
    date_fiche      DATE         NOT NULL,
    livreur_id      VARCHAR(36)  NOT NULL,
    statut          ENUM('Brouillon','En cours','Complétée','Clôturée') NOT NULL DEFAULT 'Brouillon',
    total_sorties   DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_retours   DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_net       DECIMAL(15,2) NOT NULL DEFAULT 0,
    cree_par        VARCHAR(36),
    date_creation   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_cloture    DATETIME,
    FOREIGN KEY (livreur_id) REFERENCES utilisateur(id),
    FOREIGN KEY (cree_par) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS ligne_sortie (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    fiche_id            VARCHAR(36)    NOT NULL,
    client_id           VARCHAR(36)    NOT NULL,
    produit_id          VARCHAR(36)    NOT NULL,
    quantite_sortie     INT            NOT NULL DEFAULT 0,
    quantite_retournee  INT            NOT NULL DEFAULT 0,
    quantite_nette      INT            GENERATED ALWAYS AS (quantite_sortie - quantite_retournee) STORED,
    tarif_applicable    DECIMAL(15,2)  NOT NULL,
    type_tarif          VARCHAR(50),
    remise_pct          DECIMAL(5,2)   NOT NULL DEFAULT 0,
    montant_ht          DECIMAL(15,2)  NOT NULL DEFAULT 0,
    motif_retour        VARCHAR(300),
    FOREIGN KEY (fiche_id) REFERENCES fiche_journaliere(id),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (produit_id) REFERENCES produit(id)
);

-- ─────────────────────────────────────────────
--  FACTURATION
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS facture (
    id              VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero          VARCHAR(20)    NOT NULL UNIQUE,
    date_emission   DATE           NOT NULL,
    client_id       VARCHAR(36)    NOT NULL,
    livreur_id      VARCHAR(36),
    montant_ht      DECIMAL(15,2)  NOT NULL DEFAULT 0,
    tva_pct         DECIMAL(5,2)   NOT NULL DEFAULT 0,
    tva_montant     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    montant_ttc     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    statut          ENUM('En attente','Partielle','Payée','Annulée') NOT NULL DEFAULT 'En attente',
    est_verrouillee TINYINT(1)     NOT NULL DEFAULT 1,
    est_annulee     TINYINT(1)     NOT NULL DEFAULT 0,
    fiche_id        VARCHAR(36),
    mode_reglement  VARCHAR(50),
    notes           TEXT,
    cree_par        VARCHAR(36),
    date_creation   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (livreur_id) REFERENCES utilisateur(id),
    FOREIGN KEY (fiche_id) REFERENCES fiche_journaliere(id),
    FOREIGN KEY (cree_par) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS avoir (
    id              VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero          VARCHAR(20)    NOT NULL UNIQUE,
    facture_id      VARCHAR(36)    NOT NULL,
    date_avoir      DATE           NOT NULL,
    montant         DECIMAL(15,2)  NOT NULL,
    motif           VARCHAR(300),
    cree_par        VARCHAR(36),
    date_creation   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (facture_id) REFERENCES facture(id),
    FOREIGN KEY (cree_par) REFERENCES utilisateur(id)
);

-- ─────────────────────────────────────────────
--  CAISSE
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS versement (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero              VARCHAR(20)    NOT NULL UNIQUE,
    facture_id          VARCHAR(36)    NOT NULL,
    livreur_id          VARCHAR(36),
    client_id           VARCHAR(36),
    montant_attendu     DECIMAL(15,2)  NOT NULL,
    montant_remis       DECIMAL(15,2)  NOT NULL,
    montant_enregistre  DECIMAL(15,2)  NOT NULL,
    ecart               DECIMAL(15,2)  GENERATED ALWAYS AS (montant_remis - montant_enregistre) STORED,
    mode_paiement       VARCHAR(50),
    motif_ecart         VARCHAR(300),
    date_versement      DATE           NOT NULL,
    statut              ENUM('Payé','Partiel','En attente') NOT NULL DEFAULT 'En attente',
    caissier_id         VARCHAR(36),
    date_creation       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (facture_id) REFERENCES facture(id),
    FOREIGN KEY (livreur_id) REFERENCES utilisateur(id),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (caissier_id) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS recu (
    id              VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero          VARCHAR(20)    NOT NULL UNIQUE,
    versement_id    VARCHAR(36)    NOT NULL,
    date_recu       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    genere_par      VARCHAR(36),
    FOREIGN KEY (versement_id) REFERENCES versement(id),
    FOREIGN KEY (genere_par) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS remboursement (
    id          VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    client_id   VARCHAR(36)    NOT NULL,
    montant     DECIMAL(15,2)  NOT NULL,
    motif       VARCHAR(300),
    date_remb   DATE           NOT NULL,
    cree_par    VARCHAR(36),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (cree_par) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS cloture_journaliere (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    date_cloture        DATE           NOT NULL UNIQUE,
    montant_attendu     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    montant_remis       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    montant_enregistre  DECIMAL(15,2)  NOT NULL DEFAULT 0,
    ecart_total         DECIMAL(15,2)  NOT NULL DEFAULT 0,
    motif_ecart         VARCHAR(300),
    taux_recouvrement   DECIMAL(5,2)   NOT NULL DEFAULT 0,
    solde_cloture       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    valide_par          VARCHAR(36),
    date_validation     DATETIME,
    FOREIGN KEY (valide_par) REFERENCES utilisateur(id)
);

-- ─────────────────────────────────────────────
--  JOURNAL D'AUDIT
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS journal_audit (
    id              VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    entite          VARCHAR(100) NOT NULL,
    entite_id       VARCHAR(36),
    action          VARCHAR(50)  NOT NULL,   -- CREATE, UPDATE, DELETE, LOGIN, BLOCK, ...
    utilisateur_id  VARCHAR(36),
    login_utilisateur VARCHAR(50),
    details         TEXT,
    ip_address      VARCHAR(50),
    date_action     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Journal immuable : pas de FK UPDATE/DELETE pour éviter les cascades
    INDEX idx_audit_entite (entite),
    INDEX idx_audit_utilisateur (utilisateur_id),
    INDEX idx_audit_date (date_action)
);

-- ─────────────────────────────────────────────
--  SOLDE CLIENT (table de suivi périodique)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS solde_client (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    client_id           VARCHAR(36)    NOT NULL,
    date_solde          DATE           NOT NULL,
    solde_ouverture     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    sorties_du_jour     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    versements_du_jour  DECIMAL(15,2)  NOT NULL DEFAULT 0,
    solde_cloture       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    UNIQUE KEY uk_solde_client_date (client_id, date_solde),
    FOREIGN KEY (client_id) REFERENCES client(id)
);

-- ─────────────────────────────────────────────
--  DONNÉES INITIALES
-- ─────────────────────────────────────────────

-- Rôles
INSERT IGNORE INTO role (id, nom, description) VALUES
  ('role-admin', 'ADMIN',      'Administrateur / Manager — accès complet'),
  ('role-compta', 'COMPTABLE', 'Comptable — suivi financier'),
  ('role-caissier', 'CAISSIER','Caissier — encaissements'),
  ('role-livreur', 'LIVREUR',  'Livreur — sorties/retours');

-- Permissions
INSERT IGNORE INTO permission (id, code, description) VALUES
  ('p01','PRODUIT_WRITE',    'Créer/modifier produits et tarifs'),
  ('p02','PRODUIT_READ',     'Consulter produits'),
  ('p03','CLIENT_WRITE',     'Créer/modifier clients'),
  ('p04','CLIENT_READ',      'Consulter clients'),
  ('p05','SORTIE_WRITE',     'Saisir sorties et retours'),
  ('p06','SORTIE_READ',      'Consulter fiches journalières'),
  ('p07','FACTURATION_READ', 'Consulter factures'),
  ('p08','CAISSE_WRITE',     'Enregistrer versements'),
  ('p09','CAISSE_READ',      'Consulter caisse'),
  ('p10','RECOUVREMENT_READ','Consulter recouvrement'),
  ('p11','USER_WRITE',       'Gérer utilisateurs et rôles'),
  ('p12','AUDIT_READ',       'Consulter journal d''audit'),
  ('p13','RAPPORT_READ',     'Générer rapports et exports PDF'),
  ('p14','DEBLOCAGE_WRITE',  'Débloquer un client'),
  ('p15','CLOTURE_WRITE',    'Valider clôture journalière'),
  ('p16','AVOIR_WRITE',      'Créer un avoir sur facture');

-- Permissions ADMIN (toutes)
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT 'role-admin', id FROM permission;

-- Permissions COMPTABLE
INSERT IGNORE INTO role_permission (role_id, permission_id) VALUES
  ('role-compta','p02'),('role-compta','p04'),('role-compta','p07'),
  ('role-compta','p09'),('role-compta','p10'),('role-compta','p12'),
  ('role-compta','p13'),('role-compta','p16');

-- Permissions CAISSIER
INSERT IGNORE INTO role_permission (role_id, permission_id) VALUES
  ('role-caissier','p04'),('role-caissier','p07'),
  ('role-caissier','p08'),('role-caissier','p09'),
  ('role-caissier','p13'),('role-caissier','p15');

-- Permissions LIVREUR
INSERT IGNORE INTO role_permission (role_id, permission_id) VALUES
  ('role-livreur','p04'),('role-livreur','p05'),('role-livreur','p06');

-- Familles de produits
INSERT IGNORE INTO famille (id, nom) VALUES
  ('fam-pains',      'Pains'),
  ('fam-vienno',     'Viennoiseries'),
  ('fam-patis',      'Pâtisseries'),
  ('fam-autres',     'Autres');

-- Catégories clients
INSERT IGNORE INTO categorie_client (id, nom) VALUES
  ('cat-ext',   'Externe'),
  ('cat-int',   'Interne'),
  ('cat-carre', 'Carrefour');

-- Utilisateur ADMIN par défaut  (mot de passe : Admin@2025)
-- Hash BCrypt cost=10 généré dynamiquement
INSERT IGNORE INTO utilisateur (id, login, mot_de_passe, nom_complet, role_id, actif) VALUES
  ('usr-admin', 'admin',
   '$2a$10$ihaTVCkHqHSR.y7Et6w/TusKZ2XCbK8.he15MEDNeebOJSRlRQEGa',
   'Administrateur Système', 'role-admin', 1);
