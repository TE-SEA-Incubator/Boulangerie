package com.boulangerie;

import atlantafx.base.theme.PrimerLight;
import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.ui.LoginView;
import com.boulangerie.util.DatabaseInitializer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * Point d'entrée JavaFX.
 */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) {
        // ── Thème AtlantaFX PrimerLight ───────────────────────────
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // ── Vérification connexion DB ─────────────────────────────
        try (Connection c = DatabaseConnection.getInstance().getConnection()) {
            log.info("Connexion MySQL établie.");
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de connexion");
            alert.setHeaderText("Impossible de se connecter à MySQL");
            alert.setContentText("Vérifiez db.properties et que MySQL est démarré.\n\n" + e.getMessage());
            alert.showAndWait();
            Platform.exit();
            return;
        }

        // ── Init schéma ───────────────────────────────────────────
        try {
            DatabaseInitializer.init();
        } catch (Exception e) {
            log.warn("Init schéma ignorée : {}", e.getMessage());
        }

        // ── Écran de login (taille plein écran) ───────────────────
        var bounds = Screen.getPrimary().getVisualBounds();
        LoginView loginView = new LoginView(stage);
        Scene scene = new Scene(loginView.getRoot(),
            bounds.getWidth(), bounds.getHeight());
        scene.getStylesheets().add(
            getClass().getResource("/styles/app.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("Gestion Boulangerie");
        stage.setMaximized(true);
        stage.show();

        log.info("Application Gestion Boulangerie démarrée.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
