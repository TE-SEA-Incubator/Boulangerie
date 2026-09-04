package com.boulangerie.ui.fx;

import atlantafx.base.theme.Styles;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Classe de base pour tous les panneaux — utilitaires communs.
 */
public abstract class FxPanelBase implements FxPanel {

    protected final MainWindow   mainWindow;
    protected final BorderPane   root;
    protected static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fx-panel-worker");
        t.setDaemon(true);
        return t;
    });

    protected FxPanelBase(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new BorderPane();
        root.getStyleClass().add("content-panel");
    }

    @Override
    public BorderPane getRoot() { return root; }

    // ── Utilitaires UI ────────────────────────────────────────────

    /** Titre de section */
    protected Label sectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    /** Sous-titre */
    protected Label subTitle(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("subsection-title");
        return lbl;
    }

    /** Toolbar standard */
    protected HBox toolbar(Node... nodes) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(nodes);
        return bar;
    }

    /** En-tête : titre + toolbar */
    protected VBox header(String titre, Node... toolbarNodes) {
        VBox h = new VBox(8);
        h.setPadding(new Insets(0, 0, 12, 0));
        h.getChildren().add(sectionTitle(titre));
        if (toolbarNodes.length > 0) h.getChildren().add(toolbar(toolbarNodes));
        return h;
    }

    /** Bouton primaire avec icône */
    protected Button btnPrimary(String text, BootstrapIcons icon) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        if (icon != null) {
            FontIcon fi = new FontIcon(icon);
            btn.setGraphic(fi);
        }
        btn.setCursor(javafx.scene.Cursor.HAND);
        return btn;
    }

    /** Bouton danger */
    protected Button btnDanger(String text, BootstrapIcons icon) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        if (icon != null) btn.setGraphic(new FontIcon(icon));
        btn.setCursor(javafx.scene.Cursor.HAND);
        return btn;
    }

    /** Bouton succès */
    protected Button btnSuccess(String text, BootstrapIcons icon) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        if (icon != null) btn.setGraphic(new FontIcon(icon));
        btn.setCursor(javafx.scene.Cursor.HAND);
        return btn;
    }

    /** Bouton plat (outline) */
    protected Button btnOutline(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add(Styles.FLAT);
        btn.setCursor(javafx.scene.Cursor.HAND);
        return btn;
    }

    /** Champ de recherche */
    protected TextField searchField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().addAll("search-field", Styles.ROUNDED);
        tf.setPrefWidth(220);
        tf.setMaxWidth(260);
        FontIcon searchIcon = new FontIcon(BootstrapIcons.SEARCH);
        searchIcon.setStyle("-fx-icon-color:#5F6368;");
        return tf;
    }

    /** Carte blanche avec titre */
    protected VBox whiteCard(String titre, Node content) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        if (titre != null && !titre.isBlank()) {
            Label t = subTitle(titre);
            Separator sep = new Separator();
            sep.setStyle("-fx-background-color:#DADCE0;");
            card.getChildren().addAll(t, sep);
        }
        card.getChildren().add(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    /** Badge de statut */
    protected Label badge(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("badge");
        String css = switch (text) {
            case "Actif", "Payée", "Complétée" -> "badge-success";
            case "Bloqué", "Annulée"            -> "badge-danger";
            case "Brouillon", "Partielle", "EnAttente", "En attente" -> "badge-warning";
            case "EnCours", "En cours"           -> "badge-info";
            case "Nominatif"                     -> "badge-info";
            case "Anonyme"                       -> "badge-muted";
            case "Carrefour"                     -> "badge-purple";
            default -> "badge-muted";
        };
        lbl.getStyleClass().add(css);
        return lbl;
    }

    /** Panneau KPI */
    protected VBox kpiCard(String titre, String valeur, String variation, String accentColor) {
        VBox card = new VBox(4);
        card.getStyleClass().add("kpi-card");

        // Point coloré
        HBox topRow = new HBox(4);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label lblT = new Label(titre);
        lblT.getStyleClass().add("kpi-title");
        lblT.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Text dot = new Text("●");
        dot.setStyle("-fx-fill:" + accentColor + "; -fx-font-size:10px;");
        topRow.getChildren().addAll(lblT, spacer, dot);

        Label lblV = new Label(valeur);
        lblV.getStyleClass().add("kpi-value");
        lblV.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:#202124;");

        Label lblVar = new Label(variation != null ? variation : "");
        lblVar.setStyle("-fx-font-size:11px; -fx-text-fill:"
            + (variation != null && variation.startsWith("+") ? "#0F9D58" : "#D93025") + ";");

        card.getChildren().addAll(topRow, lblV, lblVar);
        return card;
    }

    /** Exécuter en background thread puis mettre à jour l'UI */
    protected <T> void runAsync(java.util.concurrent.Callable<T> task,
                                 java.util.function.Consumer<T> onSuccess) {
        POOL.submit(() -> {
            try {
                T result = task.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                Platform.runLater(() ->
                    mainWindow.showAlert("Erreur", e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /** TableView stylisée générique */
    protected <T> TableView<T> styledTable() {
        TableView<T> table = new TableView<>();
        table.getStyleClass().addAll("styled-table", Styles.STRIPED);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    /**
     * Crée un GridPane de formulaire avec labels non tronqués.
     * Colonne 0 : largeur fixe 200px (labels)
     * Colonne 1 : hgrow ALWAYS (champs)
     */
    protected GridPane formGrid() {
        GridPane g = new GridPane();
        g.setHgap(14); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(180); c0.setPrefWidth(200); c0.setHgrow(Priority.NEVER);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS); c1.setFillWidth(true);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    /** Label de formulaire standard (non tronqué) */
    protected Label formLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:13px;");
        l.setWrapText(false);
        l.setMinWidth(Label.USE_PREF_SIZE);
        return l;
    }

    protected ScrollPane scrollPane(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    /** Label footer comptage */
    protected Label footerCount(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368; -fx-padding: 4 0 0 0;");
        return lbl;
    }

    /** Formater montant */
    protected String fmt(BigDecimal v) { return FormatUtil.montant(v); }
}
