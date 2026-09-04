package com.boulangerie.ui;

import atlantafx.base.theme.Styles;
import com.boulangerie.service.AuthService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.fx.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fenêtre principale JavaFX — maximisée, responsive.
 *
 * CORRECTIFS v3 :
 *  1. Navbar filtrée par rôle (ADMIN/COMPTABLE/CAISSIER/LIVREUR)
 *  2. Lazy loading conservé
 *  3. Design amélioré (navbar marine, bouton actif jaune)
 *  4. Stage.setMaximized(true) garanti
 */
public class MainWindow {

    private final Stage          stage;
    private final SessionService session     = SessionService.getInstance();
    private final AuthService    authService = new AuthService();

    // Cache lazy des panels
    private final Map<String, FxPanel> panelCache = new HashMap<>();

    private StackPane contentArea;
    private Label     lblHeure;
    private Button    activeNavBtn;

    // ── Constantes de navigation ──────────────────────────────────
    public static final String DASHBOARD    = "DASHBOARD";
    public static final String PRODUITS     = "PRODUITS";
    public static final String CLIENTS      = "CLIENTS";
    public static final String SORTIES      = "SORTIES";
    public static final String FACTURATION  = "FACTURATION";
    public static final String CAISSE       = "CAISSE";
    public static final String RECOUVREMENT = "RECOUVREMENT";
    public static final String UTILISATEURS = "UTILISATEURS";
    public static final String RAPPORTS     = "RAPPORTS";
    public static final String AUDIT        = "AUDIT";
    public static final String PARAMETRES   = "PARAMETRES";

    /**
     * Menus visibles par rôle (CDC §3.1 Matrice des droits)
     * Format : { label, card, permission_requise_ou_null }
     */
    private static final Map<String, List<String[]>> MENUS_PAR_ROLE = new LinkedHashMap<>();

    static {
        // ADMIN — accès complet
        MENUS_PAR_ROLE.put("ADMIN", Arrays.asList(
            new String[]{"🏠 Dashboard",    DASHBOARD,    null},
            new String[]{"📦 Produits",     PRODUITS,     null},
            new String[]{"👥 Clients",      CLIENTS,      null},
            new String[]{"📋 Sorties",      SORTIES,      null},
            new String[]{"🧾 Facturation",  FACTURATION,  null},
            new String[]{"💰 Caisse",       CAISSE,       null},
            new String[]{"📊 Recouvrement", RECOUVREMENT, null},
            new String[]{"👤 Utilisateurs", UTILISATEURS, null},
            new String[]{"📄 Rapports",     RAPPORTS,     null},
            new String[]{"🔍 Audit",        AUDIT,        null},
            new String[]{"⚙ Paramètres",   PARAMETRES,   null}
        ));
        // COMPTABLE — suivi financier
        MENUS_PAR_ROLE.put("COMPTABLE", Arrays.asList(
            new String[]{"🏠 Dashboard",    DASHBOARD,    null},
            new String[]{"👥 Clients",      CLIENTS,      null},
            new String[]{"🧾 Facturation",  FACTURATION,  null},
            new String[]{"📊 Recouvrement", RECOUVREMENT, null},
            new String[]{"📄 Rapports",     RAPPORTS,     null},
            new String[]{"🔍 Audit",        AUDIT,        null}
        ));
        // CAISSIER — encaissements
        MENUS_PAR_ROLE.put("CAISSIER", Arrays.asList(
            new String[]{"🏠 Dashboard",    DASHBOARD,    null},
            new String[]{"💰 Caisse",       CAISSE,       null},
            new String[]{"📊 Recouvrement", RECOUVREMENT, null},
            new String[]{"📄 Rapports",     RAPPORTS,     null}
        ));
        // LIVREUR — sorties/retours
        MENUS_PAR_ROLE.put("LIVREUR", Arrays.asList(
            new String[]{"🏠 Dashboard",    DASHBOARD,    null},
            new String[]{"👥 Clients",      CLIENTS,      null},
            new String[]{"📋 Sorties",      SORTIES,      null}
        ));
    }

    public MainWindow(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F6FA;");

        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #F3F6FA;");

        root.setTop(buildNavBar());
        root.setCenter(contentArea);
        root.setBottom(buildStatusBar());

        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        scene.getStylesheets().add(
            getClass().getResource("/styles/app.css").toExternalForm());

        stage.setScene(scene);
        stage.setMaximized(true);          // Garantir la maximisation
        stage.setMinWidth(1024);
        stage.setMinHeight(640);
        stage.setTitle("Gestion Boulangerie — "
            + session.getUtilisateur().getNomComplet()
            + " | " + session.getUtilisateur().getRole().getNom());
        stage.show();
        stage.setMaximized(true);          // Double appel = garanti même sur certains WM

        navigate(DASHBOARD);

        // Horloge
        Timeline clock = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> updateHeure()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        // Session inactivité (1 min)
        Timeline sessionTimer = new Timeline(
            new KeyFrame(Duration.minutes(1), e -> checkSession()));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
    }

    // ── Barre de navigation filtrée par rôle ─────────────────────
    private HBox buildNavBar() {
        HBox nav = new HBox();
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle(
            "-fx-background-color: #082B57; "
            + "-fx-pref-height: 52; -fx-min-height: 52; -fx-max-height: 52;");

        // Logo
        Label logo = new Label("  🥖  Gestion Boulangerie");
        logo.setStyle("-fx-font-size:15px; -fx-font-weight:bold; "
            + "-fx-text-fill:white; -fx-padding:0 16 0 10;");

        // Boutons filtrés selon le rôle connecté
        HBox navBtns = new HBox(0);
        navBtns.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(navBtns, Priority.ALWAYS);

        String role = session.getUtilisateur().getRole().getNom().toUpperCase();
        List<String[]> menus = MENUS_PAR_ROLE.getOrDefault(role,
            MENUS_PAR_ROLE.get("LIVREUR")); // Rôle inconnu → accès minimal

        for (String[] item : menus) {
            Button btn = makeNavBtn(item[0], item[1]);
            navBtns.getChildren().add(btn);
        }

        // Espace + utilisateur + déconnexion
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Chip utilisateur
        HBox userChip = new HBox(6);
        userChip.setAlignment(Pos.CENTER);
        userChip.setStyle("-fx-background-color:rgba(255,255,255,0.12); "
            + "-fx-background-radius:20; -fx-padding:4 12 4 12;");
        FontIcon userIcon = new FontIcon(BootstrapIcons.PERSON_CIRCLE);
        userIcon.setIconSize(14);
        userIcon.setIconColor(Color.web("#EAF2FC"));
        Label lblUser = new Label(
            session.getUtilisateur().getNomComplet()
            + "  ·  " + role);
        lblUser.setStyle("-fx-font-size:11px; -fx-text-fill:#EAF2FC;");
        userChip.getChildren().addAll(userIcon, lblUser);

        // Bouton déconnexion
        Button btnDeconn = new Button();
        FontIcon powerIcon = new FontIcon(BootstrapIcons.POWER);
        powerIcon.setIconSize(15);
        powerIcon.setIconColor(Color.web("#FF6B6B"));
        btnDeconn.setGraphic(powerIcon);
        btnDeconn.setTooltip(new Tooltip("Déconnexion"));
        btnDeconn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; "
            + "-fx-padding:0 12 0 8;");
        btnDeconn.setOnAction(e -> deconnecter());

        nav.getChildren().addAll(logo, navBtns, spacer, userChip, btnDeconn);
        nav.setPadding(new Insets(0, 6, 0, 0));
        return nav;
    }

    private Button makeNavBtn(String label, String key) {
        Button btn = new Button(label);
        btn.setStyle(NAV_BTN_STYLE_NORMAL);
        btn.setOnAction(e -> {
            navigate(key);
            setActiveNavBtn(btn);
        });
        btn.setOnMouseEntered(e -> {
            if (btn != activeNavBtn) btn.setStyle(NAV_BTN_STYLE_HOVER);
        });
        btn.setOnMouseExited(e -> {
            if (btn != activeNavBtn) btn.setStyle(NAV_BTN_STYLE_NORMAL);
        });
        return btn;
    }

    private static final String NAV_BTN_STYLE_NORMAL =
        "-fx-background-color:transparent; -fx-text-fill:#EAF2FC; "
        + "-fx-font-size:12px; -fx-padding:0 10 0 10; "
        + "-fx-pref-height:52; -fx-background-radius:0; "
        + "-fx-border-width:0 0 2 0; -fx-border-color:transparent; -fx-cursor:hand;";

    private static final String NAV_BTN_STYLE_HOVER =
        "-fx-background-color:#0D417E; -fx-text-fill:white; "
        + "-fx-font-size:12px; -fx-padding:0 10 0 10; "
        + "-fx-pref-height:52; -fx-background-radius:0; "
        + "-fx-border-width:0 0 2 0; -fx-border-color:transparent; -fx-cursor:hand;";

    private static final String NAV_BTN_STYLE_ACTIVE =
        "-fx-background-color:#FBC02D; -fx-text-fill:#082B57; "
        + "-fx-font-size:12px; -fx-font-weight:bold; -fx-padding:0 10 0 10; "
        + "-fx-pref-height:52; -fx-background-radius:0; "
        + "-fx-border-width:0 0 2 0; -fx-border-color:transparent; -fx-cursor:hand;";

    private void setActiveNavBtn(Button btn) {
        if (activeNavBtn != null) activeNavBtn.setStyle(NAV_BTN_STYLE_NORMAL);
        activeNavBtn = btn;
        btn.setStyle(NAV_BTN_STYLE_ACTIVE);
    }

    // ── Barre de statut ───────────────────────────────────────────
    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 14, 0, 14));
        bar.setStyle("-fx-background-color:#F3F6FA; "
            + "-fx-border-color:#D7E0EA transparent transparent transparent; "
            + "-fx-border-width:1 0 0 0; -fx-pref-height:28;");

        Label lblInfo = new Label(
            "Utilisateur : " + session.getUtilisateur().getNomComplet()
            + "   |   Rôle : " + session.getUtilisateur().getRole().getNom());
        lblInfo.setStyle("-fx-font-size:11px; -fx-text-fill:#536477;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        lblHeure = new Label();
        lblHeure.setStyle("-fx-font-size:11px; -fx-text-fill:#536477;");
        updateHeure();

        bar.getChildren().addAll(lblInfo, sp, lblHeure);
        return bar;
    }

    // ── Navigation lazy ───────────────────────────────────────────
    public void navigate(String key) {
        // Masquer tous
        for (var child : contentArea.getChildren()) {
            child.setVisible(false);
            child.setManaged(false);
        }
        // Créer ou récupérer
        FxPanel panel = panelCache.computeIfAbsent(key, this::createPanel);
        if (!contentArea.getChildren().contains(panel.getRoot())) {
            contentArea.getChildren().add(panel.getRoot());
        }
        panel.getRoot().setVisible(true);
        panel.getRoot().setManaged(true);
        Platform.runLater(panel::refresh);
        session.rafraichir();
    }

    private FxPanel createPanel(String key) {
        return switch (key) {
            case DASHBOARD    -> new DashboardFxPanel(this);
            case PRODUITS     -> new ProduitsFxPanel(this);
            case CLIENTS      -> new ClientsFxPanel(this);
            case SORTIES      -> new SortiesFxPanel(this);
            case FACTURATION  -> new FacturationFxPanel(this);
            case CAISSE       -> new CaisseFxPanel(this);
            case RECOUVREMENT -> new RecouvrementFxPanel(this);
            case UTILISATEURS -> new UtilisateursFxPanel(this);
            case RAPPORTS     -> new RapportsFxPanel(this);
            case AUDIT        -> new AuditFxPanel(this);
            case PARAMETRES   -> new ParametresFxPanel(this);
            default           -> new DashboardFxPanel(this);
        };
    }

    // ── Session ───────────────────────────────────────────────────
    private void checkSession() {
        if (session.isExpiree()) {
            Platform.runLater(() -> {
                showAlert("Session expirée",
                    "Votre session a expiré par inactivité.",
                    Alert.AlertType.WARNING);
                deconnecter();
            });
        }
    }

    private void deconnecter() {
        authService.deconnecter();
        panelCache.clear();
        LoginView lv = new LoginView(stage);
        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(lv.getRoot(), bounds.getWidth(), bounds.getHeight());
        scene.getStylesheets().add(
            getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMaximized(true);
    }

    // ── Utilitaires ───────────────────────────────────────────────
    private void updateHeure() {
        if (lblHeure != null) {
            lblHeure.setText(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                    "EEEE d MMMM yyyy   HH:mm:ss",
                    java.util.Locale.FRENCH)));
        }
    }

    public void showAlert(String titre, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    public SessionService getSession() { return session; }
    public Stage          getStage()   { return stage; }
}
