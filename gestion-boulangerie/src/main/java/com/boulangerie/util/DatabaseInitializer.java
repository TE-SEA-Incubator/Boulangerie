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
            log.info("Tables déjà présentes — initialisation ignorée.");
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
}
