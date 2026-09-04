package com.boulangerie;

import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.ui.LoginFrame;
import com.boulangerie.util.DatabaseInitializer;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.sql.Connection;

/**
 * Point d'entrée principal — Application Swing + FlatLaf.
 * <p>
 * Lancement :
 * 1. FlatLaf Light (moderne, palette alignée sur UIConstants)
 * 2. Vérif connexion MySQL + init schéma / données par défaut
 * 3. Ouverture LoginFrame
 */
public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {}

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightFlatIJTheme());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("Component.focusColor", new java.awt.Color(0x1A73E8));
            UIManager.put("Table.showGridLines", true);
            UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
            UIManager.put("Table.selectionBackground", new java.awt.Color(0xE8F0FE));
            UIManager.put("Table.selectionForeground", new java.awt.Color(0x202124));
        } catch (Exception ex) {
            try { UIManager.setLookAndFeel(new FlatLightLaf()); }
            catch (Exception ignored) { log.warn("FlatLaf indisponible, utilisation L&F système"); }
        }

        SwingUtilities.invokeLater(() -> {
            try (Connection c = DatabaseConnection.getInstance().getConnection()) {
                log.info("Connexion MySQL OK");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                    "<html><b>Impossible de se connecter à MySQL</b><br>"
                    + "Vérifiez db.properties et que MySQL est démarré.<br><br>"
                    + ex.getMessage() + "</html>",
                    "Erreur de connexion",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            try {
                DatabaseInitializer.init();
            } catch (Exception ex) {
                log.warn("Initialisation schéma partielle : {}", ex.getMessage());
            }

            new LoginFrame().setVisible(true);
        });
    }
}
