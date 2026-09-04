package com.boulangerie.ui.fx;

import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.service.ClotureService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.time.LocalDate;

/**
 * Module Paramètres & Administration — Planche 3 (admin uniquement).
 * CORRECTIF : suppression du double-brace initialization qui causait un crash JavaFX.
 */
public class ParametresFxPanel extends FxPanelBase {

    private final ClotureService clotureService = new ClotureService();
    private final SessionService session        = SessionService.getInstance();

    private TextField    txtNom, txtAdresse, txtTel, txtEmail, txtTva;
    private ComboBox<String> cboDevise;

    public ParametresFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:transparent;");
        tabs.getTabs().addAll(
            new Tab("🏢 Entreprise",  buildEntrepriseTab()),
            new Tab("💾 Sauvegarde",  buildSauvegardeTab()),
            new Tab("🔒 Clôture",     buildClotureTab()),
            new Tab("⚙ Système",      buildSystemeTab())
        );

        VBox body = new VBox(12);
        body.setFillWidth(true);
        body.getChildren().addAll(header("Paramètres & Administration"), tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.setCenter(body);
    }

    // ── Onglet Entreprise ─────────────────────────────────────────
    private ScrollPane buildEntrepriseTab() {
        GridPane form = new GridPane();
        form.setHgap(14); form.setVgap(12); form.setPadding(new Insets(20));

        txtNom     = new TextField("BOULANGERIE");
        txtAdresse = new TextField();
        txtTel     = new TextField();
        txtEmail   = new TextField();
        txtTva     = new TextField("0");
        cboDevise  = new ComboBox<>();
        cboDevise.getItems().addAll("FCFA","EUR","USD","MAD");
        cboDevise.setValue("FCFA");

        Label lblNote = new Label("(*) Champs obligatoires — utilisés sur les documents imprimés (factures, reçus, rapports).");
        lblNote.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");

        form.addRow(0, boldLabel("Nom de l'entreprise *"), txtNom);
        form.addRow(1, boldLabel("Adresse"),               txtAdresse);
        form.addRow(2, boldLabel("Téléphone"),             txtTel);
        form.addRow(3, boldLabel("Email"),                 txtEmail);
        form.addRow(4, boldLabel("TVA par défaut (%)"),    txtTva);
        form.addRow(5, boldLabel("Devise"),                cboDevise);
        form.add(lblNote, 0, 6, 2, 1);

        Button btnSave = btnPrimary("Enregistrer", null);
        btnSave.setOnAction(e -> mainWindow.showAlert(
            "Paramètres", "Paramètres enregistrés.", Alert.AlertType.INFORMATION));
        HBox btnRow = new HBox(btnSave);
        btnRow.setPadding(new Insets(8, 0, 0, 0));
        form.add(btnRow, 0, 7, 2, 1);

        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(200);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setFillWidth(true);
        form.getColumnConstraints().addAll(c0, c1);

        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        return sp;
    }

    // ── Onglet Sauvegarde ─────────────────────────────────────────
    private ScrollPane buildSauvegardeTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);

        box.getChildren().addAll(
            sauvegardeCard("💾  Sauvegarde manuelle de la base",
                "Exporte la base de données MySQL vers un fichier .sql sécurisé.",
                e -> sauvegarderBDD()),
            sauvegardeCard("📂  Restauration depuis une sauvegarde",
                "Importe un fichier .sql de sauvegarde.\n⚠ Remplace TOUTES les données actuelles.",
                e -> restaurerBDD()),
            sauvegardeCard("📅  Commande de sauvegarde automatique",
                "Cron Linux recommandé (quotidien à 02h00) :\n"
                + "  0 2 * * * mysqldump -u root -pMOT_DE_PASSE boulangerie "
                + "> /backups/backup_$(date +\\%F).sql",
                e -> mainWindow.showAlert("Info",
                    "Ajoutez cette ligne dans crontab -e pour une sauvegarde automatique.",
                    Alert.AlertType.INFORMATION))
        );

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        return sp;
    }

    private HBox sauvegardeCard(String titre, String desc,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16; "
            + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),4,0,0,1);");

        VBox texts = new VBox(4);
        Label t = new Label(titre);
        t.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");
        Label d = new Label(desc);
        d.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");
        d.setMaxWidth(Double.MAX_VALUE);
        d.setWrapText(true);
        texts.getChildren().addAll(t, d);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Button btn = btnPrimary("Exécuter", null);
        btn.setOnAction(action);
        card.getChildren().addAll(texts, btn);
        return card;
    }

    // ── Onglet Clôture ────────────────────────────────────────────
    private ScrollPane buildClotureTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);

        // ── Clôture journalière ───────────────────────────────────
        VBox cardJ = sectionCard("🔒 Clôture journalière",
            "Calcule les soldes de clôture de tous les clients nominatifs pour aujourd'hui.\n"
            + "Règle CDC : Solde clôture J = Solde ouverture J+1.",
            btnSuccess("Exécuter la clôture du " + FormatUtil.date(LocalDate.now()), null),
            e -> executerClotureJour());

        // ── Clôture mensuelle ─────────────────────────────────────
        Spinner<Integer> spnAnnee = new Spinner<>(2020, 2099, LocalDate.now().getYear());
        spnAnnee.setEditable(true);
        spnAnnee.setPrefWidth(90);
        ComboBox<String> cboMois = new ComboBox<>();
        cboMois.getItems().addAll("Janvier","Février","Mars","Avril","Mai","Juin",
            "Juillet","Août","Septembre","Octobre","Novembre","Décembre");
        cboMois.setValue(cboMois.getItems().get(LocalDate.now().getMonthValue() - 1));

        HBox selRow = new HBox(10, new Label("Mois :"), cboMois, new Label("Année :"), spnAnnee);
        selRow.setAlignment(Pos.CENTER_LEFT);

        Button btnMens = btnOutline("Exécuter la clôture mensuelle");
        btnMens.setOnAction(e -> executerClotureMois(
            (int) spnAnnee.getValue(),
            cboMois.getSelectionModel().getSelectedIndex() + 1));

        VBox cardM = new VBox(10);
        cardM.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16;");
        Label lblM = new Label("📅 Clôture mensuelle");
        lblM.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
        Label descM = new Label("Consolide les soldes du mois sélectionné et archive les données.");
        descM.setStyle("-fx-font-size:12px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");
        cardM.getChildren().addAll(lblM, descM, selRow, btnMens);

        box.getChildren().addAll(cardJ, cardM);

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        return sp;
    }

    private VBox sectionCard(String titre, String desc, Button btn,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16;");
        Label t = new Label(titre); t.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
        Label d = new Label(desc);
        d.setStyle("-fx-font-size:12px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");
        d.setWrapText(true);
        btn.setOnAction(action);
        card.getChildren().addAll(t, d, btn);
        return card;
    }

    // ── Onglet Système ────────────────────────────────────────────
    private ScrollPane buildSystemeTab() {
        // IMPORTANT : ne PAS appeler buildSystemeInfo() dans le constructeur
        // car la connexion DB peut bloquer le thread FX — on la charge au refresh.
        TextArea info = new TextArea("Cliquez sur « Actualiser » pour charger les informations.");
        info.setEditable(false);
        info.setFont(javafx.scene.text.Font.font("Monospace", 12));
        info.setPrefRowCount(16);

        Button btnRefresh = btnOutline("⟳  Actualiser");
        btnRefresh.setOnAction(e -> {
            // Charger hors du thread FX pour éviter le gel
            runAsync(this::buildSystemeInfo, info::setText);
        });

        VBox box = new VBox(12, info, btnRefresh);
        box.setPadding(new Insets(16));
        box.setFillWidth(true);

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        return sp;
    }

    private String buildSystemeInfo() {
        String dbStatus;
        try (var c = DatabaseConnection.getInstance().getConnection()) {
            dbStatus = "✓ Connecté — MySQL 8.x";
        } catch (Exception e) {
            dbStatus = "✗ Erreur : " + e.getMessage();
        }

        long totalMB = Runtime.getRuntime().totalMemory() / 1_048_576;
        long freeMB  = Runtime.getRuntime().freeMemory()  / 1_048_576;
        long usedMB  = totalMB - freeMB;

        return "=== Informations Système ===\n\n"
            + "Application      : Gestion Boulangerie v1.0.0\n"
            + "Stack UI         : JavaFX 21 + AtlantaFX PrimerLight\n"
            + "Java             : " + System.getProperty("java.version") + "\n"
            + "OS               : " + System.getProperty("os.name")
                + " " + System.getProperty("os.version") + "\n"
            + "Mémoire totale   : " + totalMB + " MB\n"
            + "Mémoire utilisée : " + usedMB  + " MB\n"
            + "Mémoire libre    : " + freeMB  + " MB\n"
            + "\n=== Base de données ===\n\n"
            + "Driver           : MySQL Connector/J 8.x\n"
            + "Statut           : " + dbStatus + "\n"
            + "\n=== Utilisateur connecté ===\n\n"
            + "Login            : " + session.getLogin() + "\n"
            + "Rôle             : " + (session.isConnecte()
                ? session.getUtilisateur().getRole().getNom() : "—") + "\n"
            + "Dernière conn.   : " + (session.isConnecte()
                && session.getUtilisateur().getDerniereConnexion() != null
                ? FormatUtil.dateHeure(session.getUtilisateur().getDerniereConnexion())
                : "—") + "\n";
    }

    // ── Actions ───────────────────────────────────────────────────
    private void sauvegarderBDD() {
        if (!session.isAdmin()) {
            mainWindow.showAlert("Accès","Réservé à l'administrateur.",Alert.AlertType.ERROR); return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Enregistrer la sauvegarde");
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Fichier SQL","*.sql"));
        fc.setInitialFileName("backup_boulangerie_" + LocalDate.now() + ".sql");
        File file = fc.showSaveDialog(mainWindow.getStage());
        if (file == null) return;

        runAsync(() -> {
            ProcessBuilder pb = new ProcessBuilder(
                "mysqldump","-u","root","-proot",
                "--result-file=" + file.getAbsolutePath(),
                "boulangerie");
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
        }, ok -> mainWindow.showAlert("Sauvegarde",
            ok ? "✓ Sauvegarde réussie : " + file.getName()
               : "✗ Erreur lors de la sauvegarde.",
            ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR));
    }

    private void restaurerBDD() {
        if (!session.isAdmin()) {
            mainWindow.showAlert("Accès","Réservé à l'administrateur.",Alert.AlertType.ERROR); return;
        }
        Alert warn = new Alert(Alert.AlertType.WARNING,
            "⚠ ATTENTION : toutes les données actuelles seront remplacées. Continuer ?",
            ButtonType.YES, ButtonType.NO);
        warn.setTitle("Restauration BDD"); warn.setHeaderText(null);
        warn.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Ouvrir le fichier SQL de sauvegarde");
            fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("SQL","*.sql"));
            File file = fc.showOpenDialog(mainWindow.getStage());
            if (file != null) {
                mainWindow.showAlert("Restauration",
                    "Pour restaurer, exécutez cette commande dans un terminal :\n\n"
                    + "mysql -u root -p boulangerie < "
                    + file.getAbsolutePath(),
                    Alert.AlertType.INFORMATION);
            }
        });
    }

    private void executerClotureJour() {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            mainWindow.showAlert("Accès","Permission refusée.",Alert.AlertType.ERROR); return;
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Clôturer la caisse du " + FormatUtil.date(LocalDate.now()) + " ?");
        conf.setTitle("Clôture journalière"); conf.setHeaderText(null);
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r ->
            runAsync(() -> {
                clotureService.cloturerJour(LocalDate.now()); return true;
            }, ok -> mainWindow.showAlert("Clôture",
                "✓ Clôture journalière exécutée.", Alert.AlertType.INFORMATION)));
    }

    private void executerClotureMois(int annee, int mois) {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            mainWindow.showAlert("Accès","Permission refusée.",Alert.AlertType.ERROR); return;
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Clôturer le mois " + mois + "/" + annee + " ?");
        conf.setTitle("Clôture mensuelle"); conf.setHeaderText(null);
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r ->
            runAsync(() -> {
                clotureService.cloturerMois(annee, mois); return true;
            }, ok -> mainWindow.showAlert("Clôture",
                "✓ Clôture mensuelle exécutée.", Alert.AlertType.INFORMATION)));
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:13px;");
        return l;
    }

    @Override public void refresh() {}
}
