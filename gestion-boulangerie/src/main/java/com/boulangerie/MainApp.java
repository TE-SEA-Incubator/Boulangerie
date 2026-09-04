package com.boulangerie;

import atlantafx.base.theme.PrimerLight;
import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.ui.LoginView;
import com.boulangerie.util.DatabaseInitializer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;

/**
 * Point d'entrée JavaFX.
 * - Thème AtlantaFX PrimerLight (base) + surcharge CSS charte Boulangerie
 * - Icône de l'application depuis assets/icone.png
 * - Fenêtre maximisée dès le départ
 */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) {
        // ── Thème AtlantaFX (base propre) ────────────────────────
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // ── Icône de l'application ────────────────────────────────
        try {
            var iconUrl = getClass().getClassLoader().getResource("assets/icone.png");
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            log.warn("Icône application non chargée : {}", e.getMessage());
        }

        // ── Vérification connexion DB ─────────────────────────────
        try (Connection c = DatabaseConnection.getInstance().getConnection()) {
            log.info("Connexion MySQL établie.");
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de connexion");
            alert.setHeaderText("Impossible de se connecter à MySQL");
            alert.setContentText(
                "Vérifiez db.properties et que MySQL est démarré.\n\n" + e.getMessage());
            alert.showAndWait();
            Platform.exit();
            return;
        }

        // ── Initialisation schéma si nécessaire ───────────────────
        try { DatabaseInitializer.init(); }
        catch (Exception e) { log.warn("Init schéma : {}", e.getMessage()); }

        // ── Fenêtre de login (plein écran maximisé) ───────────────
        var bounds = Screen.getPrimary().getVisualBounds();
        LoginView loginView = new LoginView(stage);

        Scene scene = new Scene(loginView.getRoot(), bounds.getWidth(), bounds.getHeight());
        scene.getStylesheets().add(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("styles/app.css")
            ).toExternalForm()
        );

        stage.setScene(scene);
        stage.setTitle("Gestion Boulangerie");
        stage.setMaximized(true);
        stage.setMinWidth(1024);
        stage.setMinHeight(640);
        stage.show();
        stage.setMaximized(true); // Double appel garanti sur tous les WM Linux

        log.info("Application Gestion Boulangerie démarrée.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
