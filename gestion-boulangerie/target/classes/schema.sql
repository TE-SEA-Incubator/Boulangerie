-- ============================================================
--  SCHEMA SQL — Gestion Boulangerie v2.0 (Refonte)
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
--  CATALOGUE PRODUITS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS famille (
    id  VARCHAR(36) NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    nom VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS produit (
    id              VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    code            VARCHAR(20)    NOT NULL UNIQUE, -- Généré automatiquement dans l'application
    libelle         VARCHAR(200)   NOT NULL,
    famille_id      VARCHAR(36),
    unite           VARCHAR(20)    NOT NULL DEFAULT 'Pièce',
    prix_vente      DECIMAL(15,2)  NOT NULL DEFAULT 0,
    statut          ENUM('Actif','Inactif') NOT NULL DEFAULT 'Actif',
    seuil_alerte    INT            NOT NULL DEFAULT 0,
    description     TEXT,
    date_creation   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (famille_id) REFERENCES famille(id)
);

-- ─────────────────────────────────────────────
--  CLIENTS & LIVREURS (Entité Unique)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categorie_client (
    id  VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    nom VARCHAR(50)  NOT NULL UNIQUE,
    taux_remise_pct DECIMAL(5,2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS client (
    id                 VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    code               VARCHAR(20)    NOT NULL UNIQUE,
    nom                VARCHAR(150)   NOT NULL,
    quartier           VARCHAR(100),
    adresse            VARCHAR(255),
    telephone          VARCHAR(20),
    email              VARCHAR(100),
    categorie_id       VARCHAR(36)    NOT NULL,
    est_anonyme        TINYINT(1)     NOT NULL DEFAULT 0,
    type_client        ENUM('Nominatif','Anonyme') NOT NULL DEFAULT 'Nominatif',
    solde_actuel       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    livreur_rattache   VARCHAR(36),
    solde_precedent    DECIMAL(15,2)  NOT NULL DEFAULT 0,
    statut             ENUM('Actif','Bloqué','Inactif') NOT NULL DEFAULT 'Actif',
    notes              TEXT,
    date_creation      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (categorie_id) REFERENCES categorie_client(id),
    FOREIGN KEY (livreur_rattache) REFERENCES utilisateur(id)
);

-- ─────────────────────────────────────────────
--  SORTIES & RETOURS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fiche_journaliere (
    id              VARCHAR(36)  NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero          VARCHAR(20)  NOT NULL UNIQUE,
    date_fiche      DATE         NOT NULL,
    livreur_id      VARCHAR(36)  NOT NULL, -- FK utilisateur
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
    prix_unitaire       DECIMAL(15,2)  NOT NULL,
    montant_ht          DECIMAL(15,2)  NOT NULL DEFAULT 0,
    motif_retour        VARCHAR(300),
    FOREIGN KEY (fiche_id) REFERENCES fiche_journaliere(id),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (produit_id) REFERENCES produit(id)
);

-- ─────────────────────────────────────────────
--  CAISSE & RÈGLEMENTS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS versement (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    numero              VARCHAR(20)    NOT NULL UNIQUE,
    fiche_id            VARCHAR(36)    NOT NULL, -- Remplacant de facture_id
    livreur_id          VARCHAR(36),
    client_id           VARCHAR(36)    NOT NULL,
    montant_attendu     DECIMAL(15,2)  NOT NULL,
    montant_remis       DECIMAL(15,2)  NOT NULL,
    ecart               DECIMAL(15,2)  GENERATED ALWAYS AS (montant_remis - montant_attendu) STORED,
    mode_paiement       VARCHAR(50),
    motif_ecart         VARCHAR(300),
    date_versement      DATE           NOT NULL,
    statut              ENUM('Payé','Partiel','En attente') NOT NULL DEFAULT 'En attente',
    caissier_id         VARCHAR(36),
    date_creation       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fiche_id) REFERENCES fiche_journaliere(id),
    FOREIGN KEY (livreur_id) REFERENCES utilisateur(id),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (caissier_id) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS cloture_journaliere (
    id                  VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    date_cloture        DATE           NOT NULL UNIQUE,
    montant_attendu     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    montant_remis       DECIMAL(15,2)  NOT NULL DEFAULT 0,
    ecart_total         DECIMAL(15,2)  NOT NULL DEFAULT 0,
    motif_ecart         VARCHAR(300),
    taux_recouvrement   DECIMAL(5,2)   NOT NULL DEFAULT 0,
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
    action          VARCHAR(50)  NOT NULL,
    utilisateur_id  VARCHAR(36),
    login_utilisateur VARCHAR(50),
    details         TEXT,
    ip_address      VARCHAR(50),
    date_action     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entite (entite),
    INDEX idx_audit_utilisateur (utilisateur_id),
    INDEX idx_audit_date (date_action)
);

-- ─────────────────────────────────────────────
--  DONNÉES INITIALES
-- ─────────────────────────────────────────────

-- Catégories clients
INSERT IGNORE INTO categorie_client (id, nom, taux_remise_pct) VALUES
  ('cat-ext',   'Externe', 0),
  ('cat-int',   'Interne', 10),
  ('cat-carre', 'Carrefour', 5);

-- Rôles
INSERT IGNORE INTO role (id, nom, description) VALUES
  ('role-admin', 'ADMIN',      'Administrateur / Manager — accès complet'),
  ('role-compta', 'COMPTABLE', 'Comptable — suivi financier'),
  ('role-caissier', 'CAISSIER','Caissier — encaissements'),
  ('role-livreur', 'LIVREUR',  'Livreur — sorties/retours');

-- Utilisateur ADMIN par défaut (mot de passe : Admin@2025)
INSERT IGNORE INTO utilisateur (id, login, mot_de_passe, nom_complet, role_id, actif) VALUES
  ('usr-admin', 'admin',
   '$2a$10$ihaTVCkHqHSR.y7Et6w/TusKZ2XCbK8.he15MEDNeebOJSRlRQEGa',
   'Administrateur Système', 'role-admin', 1);

