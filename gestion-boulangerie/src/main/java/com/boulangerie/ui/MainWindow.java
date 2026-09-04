package com.boulangerie.ui;

import atlantafx.base.theme.Styles;
import com.boulangerie.service.AuthService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.fx.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
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
    private final Map<String, Button> navButtons = new HashMap<>();

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

    private static final List<NavItem> NAV_ITEMS = List.of(
        new NavItem("Tableau de bord", DASHBOARD, null, BootstrapIcons.GRID_1X2_FILL),
        new NavItem("Produits", PRODUITS, "PRODUIT_READ", BootstrapIcons.BOX_SEAM),
        new NavItem("Clients", CLIENTS, "CLIENT_READ", BootstrapIcons.PEOPLE_FILL),
        new NavItem("Sorties", SORTIES, "SORTIE_READ", BootstrapIcons.JOURNAL_TEXT),
        new NavItem("Facturation", FACTURATION, "FACTURATION_READ", BootstrapIcons.RECEIPT_CUTOFF),
        new NavItem("Caisse", CAISSE, "CAISSE_READ", BootstrapIcons.CASH_STACK),
        new NavItem("Recouvrement", RECOUVREMENT, "RECOUVREMENT_READ", BootstrapIcons.BAR_CHART_FILL),
        new NavItem("Utilisateurs", UTILISATEURS, "USER_WRITE", BootstrapIcons.PEOPLE_FILL),
        new NavItem("Rapports", RAPPORTS, "RAPPORT_READ", BootstrapIcons.FILE_EARMARK_BAR_GRAPH_FILL),
        new NavItem("Audit", AUDIT, "AUDIT_READ", BootstrapIcons.SHIELD_CHECK),
        new NavItem("Paramètres", PARAMETRES, "CLOTURE_WRITE", BootstrapIcons.GEAR_FILL)
    );

    public MainWindow(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");

        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-panel");

        root.setTop(buildNavBar());
        root.setCenter(contentArea);
        root.setBottom(buildStatusBar());

        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        scene.getStylesheets().add(
            getClass().getResource("/styles/app.css").toExternalForm());
        installSessionActivityTracking(scene);

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
        if (navButtons.containsKey(DASHBOARD)) {
            setActiveNavBtn(navButtons.get(DASHBOARD));
        }

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
        nav.getStyleClass().add("nav-bar");

        // Logo
        HBox logoBox = new HBox(10);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        logoBox.setPadding(new Insets(0, 16, 0, 10));
        FontIcon logoIcon = new FontIcon(BootstrapIcons.BASKET2_FILL);
        logoIcon.setIconColor(Color.web("#FBC02D"));
        logoIcon.setIconSize(18);
        Label logo = new Label("Gestion Boulangerie");
        logo.getStyleClass().add("nav-logo");
        logoBox.getChildren().addAll(logoIcon, logo);

        // Boutons filtrés selon le rôle connecté
        HBox navBtns = new HBox(4);
        navBtns.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(navBtns, Priority.ALWAYS);

        for (NavItem item : getAvailableNavItems()) {
            Button btn = makeNavBtn(item);
            navButtons.put(item.key(), btn);
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
            + "  ·  " + session.getUtilisateur().getRole().getNom().toUpperCase());
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

        nav.getChildren().addAll(logoBox, navBtns, spacer, userChip, btnDeconn);
        nav.setPadding(new Insets(0, 6, 0, 0));
        return nav;
    }

    private Button makeNavBtn(NavItem item) {
        Button btn = new Button(item.label());
        btn.getStyleClass().add("nav-button");
        FontIcon icon = new FontIcon(item.icon());
        icon.setIconSize(14);
        icon.setIconColor(Color.web("#EAF2FC"));
        btn.setGraphic(icon);
        btn.setOnAction(e -> {
            navigate(item.key());
            setActiveNavBtn(btn);
        });
        return btn;
    }

    private void setActiveNavBtn(Button btn) {
        if (activeNavBtn != null) {
            activeNavBtn.getStyleClass().remove("active");
            updateNavIconColor(activeNavBtn, "#EAF2FC");
        }
        activeNavBtn = btn;
        if (!btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
        updateNavIconColor(btn, "#082B57");
    }

    // ── Barre de statut ───────────────────────────────────────────
    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 14, 0, 14));
        bar.getStyleClass().add("status-bar");

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
        if (!canAccess(key)) {
            showAlert("Accès refusé",
                "Vous ne disposez pas des autorisations nécessaires pour accéder à cet écran.",
                Alert.AlertType.WARNING);
            return;
        }
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

    private List<NavItem> getAvailableNavItems() {
        List<NavItem> items = new ArrayList<>();
        for (NavItem item : NAV_ITEMS) {
            if (item.permissionCode() == null || session.hasPermission(item.permissionCode()) || session.isAdmin()) {
                items.add(item);
            }
        }
        return items;
    }

    private boolean canAccess(String key) {
        return getAvailableNavItems().stream().anyMatch(item -> item.key().equals(key));
    }

    private void installSessionActivityTracking(Scene scene) {
        scene.addEventFilter(Event.ANY, event -> {
            Object source = event.getSource();
            if (source instanceof Node) {
                session.rafraichir();
            }
        });
    }

    private void updateNavIconColor(Button btn, String color) {
        if (btn.getGraphic() instanceof FontIcon icon) {
            icon.setIconColor(Color.web(color));
        }
    }

    private record NavItem(String label, String key, String permissionCode, BootstrapIcons icon) {}
}
