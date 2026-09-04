package com.boulangerie.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.boulangerie.service.AuthService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Écran de connexion — design moderne :
 * • Fond dégradé marine/bleu
 * • Panneau droit blanc avec carte centrale
 * • Icône vectorielle Ikonli, bouton animé
 * • Taille plein écran (même taille que les autres pages)
 */
public class LoginView {
    private static final String DEFAULT_LOGIN_BUTTON_TEXT = "Se connecter";

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

        // ── Fond dégradé gauche (marine → bleu) ──────────────────
        HBox layout = new HBox();
        layout.setFillHeight(true);

        // Panneau gauche : branding / décoration
        VBox leftPanel = buildLeftPanel();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Panneau droit : formulaire de connexion
        VBox rightPanel = buildRightPanel();
        rightPanel.setMinWidth(480);
        rightPanel.setMaxWidth(480);

        layout.getChildren().addAll(leftPanel, rightPanel);
        backdrop.getChildren().add(layout);
        return backdrop;
    }

    // ── Panneau gauche (branding) ─────────────────────────────────
    private VBox buildLeftPanel() {
        VBox panel = new VBox(24);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(60));
        panel.getStyleClass().add("login-brand-panel");

        // Icône principale
        FontIcon mainIcon = new FontIcon(BootstrapIcons.BASKET2_FILL);
        mainIcon.setIconSize(90);
        mainIcon.setIconColor(Color.web("#FBC02D"));

        Label lblApp = new Label("BOULANGERIE");
        lblApp.getStyleClass().add("login-brand-title");

        Label lblSlogan = new Label("Qualité & Tradition");
        lblSlogan.getStyleClass().add("login-brand-subtitle");

        // Séparateur décoratif
        HBox sepRow = new HBox();
        sepRow.setAlignment(Pos.CENTER);
        Region sepLeft = new Region();
        sepLeft.setStyle("-fx-background-color:#FBC02D; -fx-pref-height:2; -fx-pref-width:40; -fx-background-radius:1;");
        Label sepDot = new Label("  ◆  ");
        sepDot.setStyle("-fx-text-fill:#FBC02D; -fx-font-size:14px;");
        Region sepRight = new Region();
        sepRight.setStyle("-fx-background-color:#FBC02D; -fx-pref-height:2; -fx-pref-width:40; -fx-background-radius:1;");
        sepRow.getChildren().addAll(sepLeft, sepDot, sepRight);

        // Fonctionnalités
        VBox features = new VBox(10);
        features.setAlignment(Pos.CENTER_LEFT);
        features.setMaxWidth(340);
        for (String f : new String[]{
            "Gestion des sorties & retours journaliers",
            "Facturation automatique & verrouillée",
            "Caisse & rapprochement en temps réel",
            "Recouvrement & suivi des créances",
            "Journal d'audit immuable et traçable"
        }) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            FontIcon check = new FontIcon(BootstrapIcons.CHECK_CIRCLE_FILL);
            check.setIconSize(14);
            check.setIconColor(Color.web("#FBC02D"));
            Label lbl = new Label(f);
            lbl.setStyle("-fx-text-fill:#D7E8FB; -fx-font-size:13px;");
            row.getChildren().addAll(check, lbl);
            features.getChildren().add(row);
        }

        // Version
        Label lblVer = new Label("v1.0.0 — Gestion Boulangerie");
        lblVer.getStyleClass().add("login-version");

        panel.getChildren().addAll(mainIcon, lblApp, lblSlogan, sepRow, features, lblVer);
        return panel;
    }

    // ── Panneau droit (formulaire) ────────────────────────────────
    private VBox buildRightPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("login-form-panel");
        panel.setFillWidth(true);

        VBox card = buildCard();
        panel.getChildren().add(card);
        return panel;
    }

    private VBox buildCard() {
        VBox card = new VBox(16);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(400);
        card.setMinWidth(380);

        // Titre connexion
        Label lblConnexion = new Label("Connexion");
        lblConnexion.getStyleClass().add("login-title");
        lblConnexion.setAlignment(Pos.CENTER);
        lblConnexion.setMaxWidth(Double.MAX_VALUE);

        Label lblSub = new Label("Entrez vos identifiants pour accéder à l'application.");
        lblSub.getStyleClass().add("login-subtitle");
        lblSub.setWrapText(true);
        lblSub.setMaxWidth(Double.MAX_VALUE);

        // Séparateur
        Region sep = new Region();
        sep.setStyle("-fx-background-color:#D7E0EA; -fx-pref-height:1; -fx-max-height:1;");
        sep.setMaxWidth(Double.MAX_VALUE);

        // ── Identifiant ───────────────────────────────────────────
        Label lblLoginLbl = new Label("Identifiant");
        lblLoginLbl.getStyleClass().add("login-field-label");
        lblLoginLbl.setMaxWidth(Double.MAX_VALUE);

        txtLogin = new TextField();
        txtLogin.setPromptText("Votre identifiant de connexion");
        txtLogin.getStyleClass().addAll(Styles.ROUNDED);
        txtLogin.getStyleClass().add("login-input");
        txtLogin.setMaxWidth(Double.MAX_VALUE);
        txtLogin.setOnAction(e -> txtPassword.requestFocus());
        txtLogin.textProperty().addListener((obs, oldValue, newValue) -> {
            clearError();
            updateHint("Utilisez votre identifiant personnel fourni par l'administrateur.");
        });

        // ── Mot de passe ──────────────────────────────────────────
        Label lblPassLbl = new Label("Mot de passe");
        lblPassLbl.getStyleClass().add("login-field-label");
        lblPassLbl.setMaxWidth(Double.MAX_VALUE);

        txtPassword = new PasswordTextField();
        txtPassword.setPromptText("Votre mot de passe");
        txtPassword.getStyleClass().addAll(Styles.ROUNDED);
        txtPassword.getStyleClass().add("login-input");
        txtPassword.setMaxWidth(Double.MAX_VALUE);
        txtPassword.setOnAction(e -> connecter());
        txtPassword.textProperty().addListener((obs, oldValue, newValue) -> clearError());

        lblHint = new Label("Utilisez votre identifiant personnel fourni par l'administrateur.");
        lblHint.getStyleClass().add("login-hint");
        lblHint.setWrapText(true);
        lblHint.setMaxWidth(Double.MAX_VALUE);

        // ── Message erreur ────────────────────────────────────────
        lblError = new Label(" ");
        lblError.getStyleClass().add("login-error");
        lblError.setMaxWidth(Double.MAX_VALUE);
        lblError.setWrapText(true);
        lblError.setManaged(false);
        lblError.setVisible(false);

        // ── Bouton connexion ──────────────────────────────────────
        btnLogin = new Button(DEFAULT_LOGIN_BUTTON_TEXT);
        btnLogin.getStyleClass().add("login-btn-filled");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setGraphic(createLoginIcon());
        btnLogin.setOnAction(e -> connecter());

        progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("login-progress");
        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        Label lblActionInfo = new Label("Accès sécurisé avec contrôle de session et journalisation.");
        lblActionInfo.getStyleClass().add("login-hint");
        lblActionInfo.setWrapText(true);
        lblActionInfo.setMaxWidth(Double.MAX_VALUE);

        // ── Info sécurité ─────────────────────────────────────────
        HBox infoRow = new HBox(6);
        infoRow.setAlignment(Pos.CENTER);
        FontIcon lockIcon = new FontIcon(BootstrapIcons.SHIELD_LOCK);
        lockIcon.setIconSize(12);
        lockIcon.setIconColor(Color.web("#536477"));
        Label lblInfo = new Label("Déconnexion automatique après inactivité");
        lblInfo.getStyleClass().add("login-info");
        infoRow.getChildren().addAll(lockIcon, lblInfo);

        GridPane quickInfo = new GridPane();
        quickInfo.getStyleClass().add("login-quick-info");
        quickInfo.setHgap(10);
        quickInfo.setVgap(10);
        quickInfo.setMaxWidth(Double.MAX_VALUE);
        addQuickInfoItem(quickInfo, 0, 0, "Sécurité", "Protection locale contre les échecs répétés.");
        addQuickInfoItem(quickInfo, 1, 0, "Session", "Expiration automatique après inactivité.");
        addQuickInfoItem(quickInfo, 0, 1, "Traçabilité", "Toutes les connexions sont auditées.");
        addQuickInfoItem(quickInfo, 1, 1, "Support", "Contactez l'admin en cas de blocage.");

        // ── Assemblage ────────────────────────────────────────────
        card.getChildren().addAll(
            lblConnexion, lblSub, sep,
            lblLoginLbl, txtLogin,
            lblPassLbl, txtPassword,
            lblHint,
            lblError,
            lblActionInfo,
            btnLogin,
            progressIndicator,
            quickInfo,
            infoRow
        );
        return card;
    }

    private void connecter() {
        String login = txtLogin.getText().trim();
        String mdp   = txtPassword.getPassword();

        if (login.isEmpty() || mdp.isEmpty()) {
            showError("Veuillez saisir votre identifiant et mot de passe.");
            return;
        }
        clearError();
        updateHint("Vérification des informations de connexion...");
        setLoadingState(true);

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
                        updateHint("Vérifiez vos informations puis réessayez.");
                        setLoadingState(false);
                    } else {
                        updateHint("Connexion réussie. Chargement de l'espace de travail...");
                        new MainWindow(stage).show();
                    }
                });
            }
        };
        new Thread(task, "login-thread").start();
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void clearError() {
        lblError.setText(" ");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void updateHint(String message) {
        lblHint.setText(message);
    }

    private void setLoadingState(boolean loading) {
        btnLogin.setDisable(loading);
        txtLogin.setDisable(loading);
        txtPassword.setDisable(loading);
        btnLogin.setText(loading ? "Connexion en cours..." : DEFAULT_LOGIN_BUTTON_TEXT);
        btnLogin.setGraphic(loading ? null : createLoginIcon());
        progressIndicator.setVisible(loading);
        progressIndicator.setManaged(loading);
    }

    private FontIcon createLoginIcon() {
        FontIcon icon = new FontIcon(BootstrapIcons.BOX_ARROW_IN_RIGHT);
        icon.setIconColor(Color.WHITE);
        return icon;
    }

    private void addQuickInfoItem(GridPane quickInfo, int column, int row, String title, String value) {
        VBox item = new VBox(2);
        item.getStyleClass().add("login-quick-info-item");

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("login-quick-info-title");

        Label lblValue = new Label(value);
        lblValue.getStyleClass().add("login-quick-info-value");
        lblValue.setWrapText(true);

        item.getChildren().addAll(lblTitle, lblValue);
        quickInfo.add(item, column, row);
        GridPane.setHgrow(item, Priority.ALWAYS);
        GridPane.setFillWidth(item, true);
        GridPane.setHalignment(item, HPos.LEFT);
    }
}
