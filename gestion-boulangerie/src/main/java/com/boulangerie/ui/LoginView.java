package com.boulangerie.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.boulangerie.service.AuthService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
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

    private final Stage       stage;
    private final AuthService authService = new AuthService();
    private final StackPane   root;

    private TextField         txtLogin;
    private PasswordTextField txtPassword;
    private Label             lblError;
    private Button            btnLogin;

    public LoginView(Stage stage) {
        this.stage = stage;
        root = build();
    }

    public StackPane getRoot() { return root; }

    private StackPane build() {
        StackPane backdrop = new StackPane();

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
        panel.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #082B57, #1267C4);");

        // Icône principale
        FontIcon mainIcon = new FontIcon(BootstrapIcons.BASKET2_FILL);
        mainIcon.setIconSize(90);
        mainIcon.setIconColor(Color.web("#FBC02D"));

        Label lblApp = new Label("BOULANGERIE");
        lblApp.setStyle("-fx-font-size:32px; -fx-font-weight:bold; -fx-text-fill:white; -fx-letter-spacing:3;");

        Label lblSlogan = new Label("Qualité & Tradition");
        lblSlogan.setStyle("-fx-font-size:16px; -fx-text-fill:#EAF2FC; -fx-font-style:italic;");

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
        lblVer.setStyle("-fx-text-fill:#5B8FC2; -fx-font-size:11px;");

        panel.getChildren().addAll(mainIcon, lblApp, lblSlogan, sepRow, features, lblVer);
        return panel;
    }

    // ── Panneau droit (formulaire) ────────────────────────────────
    private VBox buildRightPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: #F3F6FA;");
        panel.setFillWidth(true);

        VBox card = buildCard();
        panel.getChildren().add(card);
        return panel;
    }

    private VBox buildCard() {
        VBox card = new VBox(16);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(
            "-fx-background-color: white; "
            + "-fx-background-radius: 14; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.14), 20, 0, 0, 4); "
            + "-fx-padding: 40 44 40 44;");
        card.setMaxWidth(400);
        card.setMinWidth(380);

        // Titre connexion
        Label lblConnexion = new Label("Connexion");
        lblConnexion.setStyle("-fx-font-size:24px; -fx-font-weight:bold; -fx-text-fill:#082B57;");
        lblConnexion.setAlignment(Pos.CENTER);
        lblConnexion.setMaxWidth(Double.MAX_VALUE);

        Label lblSub = new Label("Entrez vos identifiants pour accéder à l'application.");
        lblSub.setStyle("-fx-font-size:12px; -fx-text-fill:#536477; -fx-wrap-text:true;");
        lblSub.setWrapText(true);
        lblSub.setMaxWidth(Double.MAX_VALUE);

        // Séparateur
        Region sep = new Region();
        sep.setStyle("-fx-background-color:#D7E0EA; -fx-pref-height:1; -fx-max-height:1;");
        sep.setMaxWidth(Double.MAX_VALUE);

        // ── Identifiant ───────────────────────────────────────────
        Label lblLoginLbl = new Label("Identifiant");
        lblLoginLbl.setStyle("-fx-font-weight:bold; -fx-font-size:13px; -fx-text-fill:#082B57;");
        lblLoginLbl.setMaxWidth(Double.MAX_VALUE);

        txtLogin = new TextField();
        txtLogin.setPromptText("Votre identifiant de connexion");
        txtLogin.getStyleClass().addAll(Styles.ROUNDED);
        txtLogin.setStyle("-fx-font-size:13px; -fx-pref-height:40;");
        txtLogin.setMaxWidth(Double.MAX_VALUE);
        txtLogin.setOnAction(e -> txtPassword.requestFocus());

        // ── Mot de passe ──────────────────────────────────────────
        Label lblPassLbl = new Label("Mot de passe");
        lblPassLbl.setStyle("-fx-font-weight:bold; -fx-font-size:13px; -fx-text-fill:#082B57;");
        lblPassLbl.setMaxWidth(Double.MAX_VALUE);

        txtPassword = new PasswordTextField();
        txtPassword.setPromptText("Votre mot de passe");
        txtPassword.getStyleClass().addAll(Styles.ROUNDED);
        txtPassword.setStyle("-fx-font-size:13px; -fx-pref-height:40;");
        txtPassword.setMaxWidth(Double.MAX_VALUE);
        txtPassword.setOnAction(e -> connecter());

        // ── Message erreur ────────────────────────────────────────
        lblError = new Label(" ");
        lblError.setStyle("-fx-font-size:12px; -fx-text-fill:#D93025; "
            + "-fx-background-color:#FCE8E6; -fx-background-radius:6; "
            + "-fx-padding:6 10; -fx-wrap-text:true;");
        lblError.setMaxWidth(Double.MAX_VALUE);
        lblError.setWrapText(true);

        // ── Bouton connexion ──────────────────────────────────────
        btnLogin = new Button("Se connecter");
        btnLogin.getStyleClass().add("login-btn-filled");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        FontIcon icon = new FontIcon(BootstrapIcons.BOX_ARROW_IN_RIGHT);
        icon.setIconColor(Color.WHITE);
        btnLogin.setGraphic(icon);
        btnLogin.setOnAction(e -> connecter());

        // ── Info sécurité ─────────────────────────────────────────
        HBox infoRow = new HBox(6);
        infoRow.setAlignment(Pos.CENTER);
        FontIcon lockIcon = new FontIcon(BootstrapIcons.SHIELD_LOCK);
        lockIcon.setIconSize(12);
        lockIcon.setIconColor(Color.web("#536477"));
        Label lblInfo = new Label("Déconnexion automatique après inactivité");
        lblInfo.setStyle("-fx-font-size:11px; -fx-text-fill:#536477;");
        infoRow.getChildren().addAll(lockIcon, lblInfo);

        // ── Assemblage ────────────────────────────────────────────
        card.getChildren().addAll(
            lblConnexion, lblSub, sep,
            lblLoginLbl, txtLogin,
            lblPassLbl, txtPassword,
            lblError,
            btnLogin,
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
        btnLogin.setDisable(true);
        btnLogin.setText("Connexion en cours…");
        btnLogin.setGraphic(null);

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
                        btnLogin.setDisable(false);
                        btnLogin.setText("Se connecter");
                        FontIcon icon = new FontIcon(BootstrapIcons.BOX_ARROW_IN_RIGHT);
                        icon.setIconColor(Color.WHITE);
                        btnLogin.setGraphic(icon);
                    } else {
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
    }

    private void clearError() {
        lblError.setText(" ");
    }
}
