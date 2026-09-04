package com.boulangerie.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestionnaire de connexion MySQL (singleton simple, non poolé).
 * Pour une version production, remplacer par HikariCP.
 */
public class DatabaseConnection {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static DatabaseConnection instance;

    private final String url;
    private final String username;
    private final String password;

    private DatabaseConnection() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) throw new RuntimeException("db.properties introuvable dans le classpath");
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de charger db.properties", e);
        }
        // Les variables d'environnement permettent de ne pas exposer les
        // identifiants de production dans le fichier embarqué dans le JAR.
        this.url      = configuration("BOULANGERIE_DB_URL", props, "db.url", null);
        this.username = configuration("BOULANGERIE_DB_USERNAME", props, "db.username", null);
        this.password = configuration("BOULANGERIE_DB_PASSWORD", props, "db.password", "");
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) instance = new DatabaseConnection();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private static String configuration(String variable, Properties props, String cle, String defaut) {
        String valeur = System.getenv(variable);
        return valeur != null && !valeur.isBlank() ? valeur : props.getProperty(cle, defaut);
    }

    /** Utilitaire : fermer silencieusement une connexion */
    public static void close(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { log.warn("Erreur fermeture connexion", e); }
        }
    }
}
