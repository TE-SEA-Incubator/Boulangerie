package com.boulangerie.ui;

import atlantafx.base.controls.PasswordTextField;
import com.boulangerie.service.AuthService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;

/**
 * Page de connexion — charte officielle Boulangerie.
 *
 * Couleurs :
 *   Bleu nuit  #1F3A5F  |  Bleu moyen #2E5A88
 *   Ambre/Or   #F5A623  |  Crème      #FFF3E0
 *
 * Panneau gauche : fond bleu nuit, logo + boulanger + liste features
 * Panneau droit  : fond crème, carte blanche avec formulaire épuré
 */
public class LoginView {
    private static final String DEFAULT_BTN_TEXT = "Se connecter";

    private final Stage       stage;
    private final AuthService authService = new AuthService();
    private final StackPane   root;

    private TextField         txtLogin;
    private PasswordTextField txtPassword;
    private Label             lblError;
    private Label             lblHint;
    private Button            btnLogin;
    private ProgressIndicator progressIndicator;

    public LoginView(Stage stage) {
        this.stage = stage;
        root = build();
    }

    public StackPane getRoot() { return root; }

    private StackPane build() {
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("login-root");

        HBox layout = new HBox();
        layout.setFillHeight(true);

        VBox left  = buildLeftPanel();
        VBox right = buildRightPanel();
        right.setMinWidth(500); right.setMaxWidth(500);
        HBox.setHgrow(left, Priority.ALWAYS);

        layout.getChildren().addAll(left, right);
        backdrop.getChildren().add(layout);
        return backdrop;
    }

    // ── Panneau gauche : Bleu nuit + logo + boulanger ─────────────
    private VBox buildLeftPanel() {
        VBox panel = new VBox(20);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(40, 50, 40, 50));
        panel.getStyleClass().add("login-brand-panel");

        // Logo principal (grand, centré)
        ImageView logo = loadImage("assets/logo.png", 220, 220);

        // Titre
        Label lblApp = new Label("BOULANGERIE");
        lblApp.getStyleClass().add("login-brand-title");

        Label lblSlogan = new Label("Gestion des entrées & sorties");
        lblSlogan.getStyleClass().add("login-brand-subtitle");

        // Séparateur bleu nuit (contraste sur fond orange)
        HBox golden = new HBox(8);
        golden.setAlignment(Pos.CENTER);
        Region sl = new Region();
        sl.setStyle("-fx-background-color:#1F3A5F; -fx-pref-height:2; -fx-pref-width:50; -fx-background-radius:1;");
        Label dot = new Label("◆");
        dot.setStyle("-fx-text-fill:#1F3A5F; -fx-font-size:12px;");
        Region sr = new Region();
        sr.setStyle("-fx-background-color:#1F3A5F; -fx-pref-height:2; -fx-pref-width:50; -fx-background-radius:1;");
        golden.getChildren().addAll(sl, dot, sr);

        // Features — texte bleu nuit sur fond orange
        VBox features = new VBox(8);
        features.setAlignment(Pos.CENTER_LEFT);
        features.setMaxWidth(340);
        String[] items = {
            "Gestion des sorties & retours journaliers",
            "Facturation automatique & verrouillée",
            "Caisse & rapprochement en temps réel",
            "Recouvrement & suivi des créances",
            "Journal d'audit immuable et traçable"
        };
        for (String txt : items) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            FontIcon icon = new FontIcon(BootstrapIcons.CHECK_CIRCLE_FILL);
            icon.setIconSize(13);
            icon.setIconColor(Color.web("#1F3A5F"));
            Label lbl = new Label(txt);
            // Bleu nuit sur fond orange = contraste élevé
            lbl.setStyle("-fx-text-fill:#1F3A5F; -fx-font-size:13px; -fx-font-weight:bold;");
            row.getChildren().addAll(icon, lbl);
            features.getChildren().add(row);
        }

        // Image boulanger en bas
        ImageView baker = loadImage("assets/Image 3.png", 150, 150);
        if (baker != null) {
            Rectangle clip = new Rectangle(150, 150);
            clip.setArcWidth(18); clip.setArcHeight(18);
            baker.setClip(clip);
        }

        Label lblVer = new Label("v1.0.0 — Gestion Boulangerie");
        lblVer.getStyleClass().add("login-version");

        if (logo != null) panel.getChildren().add(logo);
        panel.getChildren().addAll(lblApp, lblSlogan, golden, features);
        if (baker != null) panel.getChildren().add(baker);
        panel.getChildren().add(lblVer);
        return panel;
    }

    // ── Panneau droit : Crème + carte blanche ─────────────────────
    private VBox buildRightPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("login-form-panel");
        panel.setFillWidth(true);
        panel.getChildren().add(buildCard());
        return panel;
    }

    private VBox buildCard() {
        VBox card = new VBox(14);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(420); card.setMinWidth(380);

        // Titre
        Label lblTitre = new Label("Connexion");
        lblTitre.getStyleClass().add("login-title");
        lblTitre.setMaxWidth(Double.MAX_VALUE);
        lblTitre.setAlignment(Pos.CENTER_LEFT);

        Label lblSub = new Label("Entrez vos identifiants pour accéder à l'espace de gestion.");
        lblSub.getStyleClass().add("login-subtitle");
        lblSub.setWrapText(true); lblSub.setMaxWidth(Double.MAX_VALUE);

        // Séparateur
        Region sep = new Region();
        sep.setMaxWidth(Double.MAX_VALUE);
        sep.getStyleClass().add("section-divider");

        // ── Identifiant ───────────────────────────────────────────
        Label lblLoginLbl = new Label("Identifiant");
        lblLoginLbl.getStyleClass().add("login-field-label");
        lblLoginLbl.setMaxWidth(Double.MAX_VALUE);

        txtLogin = new TextField();
        txtLogin.setPromptText("admin");
        txtLogin.getStyleClass().add("login-input");
        txtLogin.setMaxWidth(Double.MAX_VALUE);
        txtLogin.setOnAction(e -> txtPassword.requestFocus());
        txtLogin.textProperty().addListener((o, ov, nv) -> clearError());

        // ── Mot de passe ──────────────────────────────────────────
        Label lblPassLbl = new Label("Mot de passe");
        lblPassLbl.getStyleClass().add("login-field-label");
        lblPassLbl.setMaxWidth(Double.MAX_VALUE);

        txtPassword = new PasswordTextField();
        txtPassword.setPromptText("••••••••••");
        txtPassword.getStyleClass().add("login-input");
        txtPassword.setMaxWidth(Double.MAX_VALUE);
        txtPassword.setOnAction(e -> connecter());
        txtPassword.textProperty().addListener((o, ov, nv) -> clearError());

        // ── Erreur ────────────────────────────────────────────────
        lblError = new Label(" ");
        lblError.getStyleClass().add("login-error");
        lblError.setMaxWidth(Double.MAX_VALUE);
        lblError.setWrapText(true);
        lblError.setVisible(false); lblError.setManaged(false);

        // ── Hint ──────────────────────────────────────────────────
        lblHint = new Label("Utilisez votre identifiant fourni par l'administrateur.");
        lblHint.getStyleClass().add("login-hint");
        lblHint.setWrapText(true); lblHint.setMaxWidth(Double.MAX_VALUE);

        // ── Bouton ────────────────────────────────────────────────
        btnLogin = new Button(DEFAULT_BTN_TEXT);
        btnLogin.getStyleClass().add("login-btn-filled");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setGraphic(createBtnIcon());
        btnLogin.setOnAction(e -> connecter());

        // Progress
        progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("login-progress");
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false); progressIndicator.setManaged(false);

        // ── Info sécurité ─────────────────────────────────────────
        HBox secRow = new HBox(6);
        secRow.setAlignment(Pos.CENTER);
        FontIcon shield = new FontIcon(BootstrapIcons.SHIELD_LOCK);
        shield.setIconSize(12); shield.setIconColor(Color.web("#6B7A8D"));
        Label lblSec = new Label("Accès sécurisé — déconnexion automatique après inactivité");
        lblSec.getStyleClass().add("login-info");
        secRow.getChildren().addAll(shield, lblSec);

        // ── Grille info rapide ────────────────────────────────────
        GridPane quickInfo = new GridPane();
        quickInfo.setHgap(10); quickInfo.setVgap(8);
        quickInfo.setMaxWidth(Double.MAX_VALUE);
        addInfo(quickInfo, 0, 0, "🔒 Sécurité", "Protection contre les échecs répétés.");
        addInfo(quickInfo, 1, 0, "⏱ Session", "Expiration automatique par inactivité.");
        addInfo(quickInfo, 0, 1, "📋 Traçabilité", "Toutes les connexions sont auditées.");
        addInfo(quickInfo, 1, 1, "👤 Support", "Contactez l'admin en cas de blocage.");
        for (int c = 0; c < 2; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setFillWidth(true);
            quickInfo.getColumnConstraints().add(cc);
        }

        card.getChildren().addAll(
            lblTitre, lblSub, sep,
            lblLoginLbl, txtLogin,
            lblPassLbl, txtPassword,
            lblHint, lblError,
            btnLogin, progressIndicator,
            quickInfo, secRow
        );
        return card;
    }

    // ── Logique de connexion ──────────────────────────────────────
    private void connecter() {
        String login = txtLogin.getText().trim();
        String mdp   = txtPassword.getPassword();
        if (login.isEmpty() || mdp.isEmpty()) {
            showError("Veuillez saisir votre identifiant et mot de passe.");
            return;
        }
        clearError();
        setLoading(true);
        lblHint.setText("Vérification en cours…");

        Task<Void> task = new Task<>() {
            private String errorMsg;
            @Override protected Void call() {
                try { authService.connecter(login, mdp); }
                catch (Exception ex) { errorMsg = ex.getMessage(); }
                return null;
            }
            @Override protected void succeeded() {
                Platform.runLater(() -> {
                    if (errorMsg != null) {
                        showError(errorMsg);
                        txtPassword.clear();
                        txtPassword.requestFocus();
                        lblHint.setText("Vérifiez vos informations puis réessayez.");
                        setLoading(false);
                    } else {
                        lblHint.setText("Connexion réussie. Chargement…");
                        new MainWindow(stage).show();
                    }
                });
            }
        };
        new Thread(task, "login-thread").start();
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void showError(String msg) {
        lblError.setText(msg); lblError.setVisible(true); lblError.setManaged(true);
    }
    private void clearError() {
        lblError.setVisible(false); lblError.setManaged(false);
    }
    private void setLoading(boolean on) {
        btnLogin.setDisable(on);
        txtLogin.setDisable(on);
        txtPassword.setDisable(on);
        btnLogin.setText(on ? "Connexion en cours…" : DEFAULT_BTN_TEXT);
        btnLogin.setGraphic(on ? null : createBtnIcon());
        progressIndicator.setVisible(on); progressIndicator.setManaged(on);
    }
    private FontIcon createBtnIcon() {
        FontIcon fi = new FontIcon(BootstrapIcons.BOX_ARROW_IN_RIGHT);
        fi.setIconColor(Color.WHITE);
        return fi;
    }
    private void addInfo(GridPane g, int col, int row, String title, String val) {
        VBox item = new VBox(2);
        item.getStyleClass().add("login-quick-info-item");
        Label t = new Label(title); t.getStyleClass().add("login-quick-info-title");
        Label v = new Label(val);   v.getStyleClass().add("login-quick-info-value");
        v.setWrapText(true);
        item.getChildren().addAll(t, v);
        g.add(item, col, row);
        GridPane.setHgrow(item, Priority.ALWAYS);
        GridPane.setFillWidth(item, true);
    }

    /** Charge une image depuis le classpath resources */
    private ImageView loadImage(String path, double w, double h) {
        try {
            var url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            Image img = new Image(url.toExternalForm(), w, h, true, true);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w); iv.setFitHeight(h);
            iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) {
            return null;
        }
    }
}
