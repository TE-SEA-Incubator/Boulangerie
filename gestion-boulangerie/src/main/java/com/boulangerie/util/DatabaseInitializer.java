package com.boulangerie.util;

import com.boulangerie.dao.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Vérifie si les tables existent. Si non, exécute schema.sql en entier.
 * Utilise allowMultiQueries=true dans l'URL JDBC.
 */
public class DatabaseInitializer {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    public static void init() throws Exception {
        // Vérifier si les tables existent déjà
        if (tablesExistent()) {
            log.info("Tables déjà présentes — vérification des migrations nécessaires...");
            appliquerMigrations();
            return;
        }

        log.info("Tables absentes — initialisation du schéma...");
        InputStream is = DatabaseInitializer.class.getClassLoader()
                .getResourceAsStream("schema.sql");
        if (is == null) {
            log.warn("schema.sql introuvable dans le classpath.");
            return;
        }

        String sql;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }

        // Retirer les lignes de commentaires avant le découpage : un lot qui
        // commence par "--" ne doit pas faire ignorer le CREATE TABLE suivant.
        sql = sql.replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");

        // Exécution instruction par instruction afin d'identifier les erreurs.
        try (Connection c = DatabaseConnection.getInstance().getConnection();
             Statement st = c.createStatement()) {
            // Chaque instruction du schéma se termine par un point-virgule.
            String[] statements = sql.split(";\\s*\n");
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    st.execute(trimmed);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("already exists")
                            || msg.contains("Duplicate entry")
                            || msg.contains("1050")
                            || msg.contains("1062")) {
                        log.debug("Ignoré (déjà existant): {}", msg);
                    } else {
                        log.warn("Init SQL [{}...]: {}", trimmed.substring(0, Math.min(40, trimmed.length())), msg);
                    }
                }
            }
        }
        log.info("Initialisation du schéma terminée.");
    }

    private static boolean tablesExistent() {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                   + "WHERE table_schema='boulangerie' AND table_name='utilisateur'";
        try (Connection c = DatabaseConnection.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            log.warn("Vérification tables: {}", e.getMessage());
            return false;
        }
    }

    private static void appliquerMigrations() {
        try (Connection c = DatabaseConnection.getInstance().getConnection();
             Statement st = c.createStatement()) {
            // Migration Produit: prix_unitaire
            try { st.execute("ALTER TABLE produit ADD COLUMN prix_unitaire DECIMAL(15,2) NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            // Migration Client: adresse, livreur_rattache, solde_precedent
            try { st.execute("ALTER TABLE client ADD COLUMN adresse VARCHAR(255)"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE client ADD COLUMN livreur_rattache VARCHAR(36)"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE client ADD COLUMN solde_precedent DECIMAL(15,2) DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("UPDATE client SET adresse = ville WHERE (adresse IS NULL OR adresse = '') AND ville IS NOT NULL"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE categorie_client ADD COLUMN pourcentage_remise DECIMAL(5,2) DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE ligne_commande ADD COLUMN tarif_applicable DECIMAL(15,2) DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE ligne_commande ADD COLUMN remise_pct DECIMAL(5,2) DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE ligne_commande ADD COLUMN type_tarif VARCHAR(50) DEFAULT 'Standard'"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE ligne_commande ADD COLUMN modifie_par VARCHAR(100)"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE fiche_journaliere ADD COLUMN type_fiche VARCHAR(20) DEFAULT 'SORTIE'"); } catch (Exception ignored) {}
            try { st.execute("UPDATE ligne_commande SET tarif_applicable = prix_unitaire WHERE tarif_applicable IS NULL OR tarif_applicable = 0"); } catch (Exception ignored) {}
            try {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS tarif_client (
                        id          VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
                        client_id   VARCHAR(36)    NOT NULL,
                        produit_id  VARCHAR(36)    NOT NULL,
                        prix        DECIMAL(15,2)  NOT NULL,
                        date_debut  DATE           NOT NULL,
                        date_fin    DATE,
                        actif       TINYINT(1)     NOT NULL DEFAULT 1,
                        FOREIGN KEY (client_id)  REFERENCES client(id),
                        FOREIGN KEY (produit_id) REFERENCES produit(id)
                    )""");
            } catch (Exception ignored) {}
            try {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS facture (
                        id               VARCHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
                        numero           VARCHAR(20)    NOT NULL UNIQUE,
                        date_emission    DATE           NOT NULL,
                        client_id        VARCHAR(36),
                        livreur_id       VARCHAR(36),
                        montant_ht       DECIMAL(15,2)  NOT NULL DEFAULT 0,
                        tva_pct          DECIMAL(5,2)   NOT NULL DEFAULT 0,
                        tva_montant      DECIMAL(15,2)  NOT NULL DEFAULT 0,
                        montant_ttc      DECIMAL(15,2)  NOT NULL DEFAULT 0,
                        statut           VARCHAR(30)    NOT NULL DEFAULT 'En attente',
                        est_verrouillee  TINYINT(1)     NOT NULL DEFAULT 0,
                        est_annulee      TINYINT(1)     NOT NULL DEFAULT 0,
                        fiche_id         VARCHAR(36),
                        mode_reglement   VARCHAR(50),
                        notes            TEXT,
                        cree_par         VARCHAR(36),
                        date_creation    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            } catch (Exception ignored) {}
            try {
                st.execute("""
                    INSERT IGNORE INTO produit (id, code, libelle, unite, prix_vente, prix_unitaire, statut) VALUES
                    (UUID(), 'PAI-40', '40', 'Pièce', 40.00, 40.00, 'Actif'),
                    (UUID(), 'PAI-80', '80', 'Pièce', 80.00, 80.00, 'Actif'),
                    (UUID(), 'PAI-100', '100', 'Pièce', 100.00, 100.00, 'Actif'),
                    (UUID(), 'PAI-125', '125', 'Pièce', 125.00, 125.00, 'Actif'),
                    (UUID(), 'PAI-160', '160', 'Pièce', 160.00, 160.00, 'Actif'),
                    (UUID(), 'PAI-EXTRA', 'EXTRA', 'Pièce', 200.00, 200.00, 'Actif'),
                    (UUID(), 'PJ-80', 'PJ 80', 'Pièce', 80.00, 80.00, 'Actif'),
                    (UUID(), 'PJ-100', 'PJ 100', 'Pièce', 100.00, 100.00, 'Actif'),
                    (UUID(), 'S-VIDE', 'S.VIDE', 'Pièce', 0.00, 0.00, 'Actif')
                """);
            } catch (Exception ignored) {}
            log.info("Migrations automatiques appliquées avec succès.");
        } catch (Exception e) {
            log.warn("Migration check error: {}", e.getMessage());
        }
    }
}
