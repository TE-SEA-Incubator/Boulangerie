package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.JournalAudit;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard — chargement lazy, données en background.
 * FIX : IndexOutOfBoundsException sur les KPI cards corrigé
 *       via références directes aux labels (pas d'accès par index).
 */
public class DashboardFxPanel extends FxPanelBase {

    private final FactureDAO          factureDAO   = new FactureDAO();
    private final ClientDAO           clientDAO    = new ClientDAO();
    private final FicheJournaliereDAO ficheDAO     = new FicheJournaliereDAO();
    private final VersementDAO        versementDAO = new VersementDAO();
    private final AuditDAO            auditDAO     = new AuditDAO();

    // Références directes aux labels KPI (plus d'accès par index)
    private Label lblCA, lblSorties, lblCreances, lblBloques, lblEcarts;
    private Label lblVarCA, lblVarEcarts;
    private VBox  pnlActivite, pnlAlertes;

    public DashboardFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        // Titre + date
        Label titre = sectionTitle("Tableau de bord");
        String dateStr = LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH));
        dateStr = Character.toUpperCase(dateStr.charAt(0)) + dateStr.substring(1);
        Label lblDate = new Label(dateStr);
        lblDate.setStyle("-fx-font-size:12px; -fx-text-fill:#546E7A;");

        VBox titleBox = new VBox(2, titre, lblDate);
        titleBox.setPadding(new Insets(0, 0, 10, 0));

        // KPIs
        HBox kpiRow = buildKpiRow();

        // Contenu central
        HBox midRow = buildMidRow();
        VBox.setVgrow(midRow, Priority.ALWAYS);

        VBox body = new VBox(14, titleBox, kpiRow, midRow);
        body.setFillWidth(true);
        VBox.setVgrow(midRow, Priority.ALWAYS);
        root.setCenter(body);
    }

    // ── KPI cards ────────────────────────────────────────────────
    private HBox buildKpiRow() {
        HBox row = new HBox(12);

        // Créer chaque carte et garder une référence directe aux labels
        KpiCard ca       = new KpiCard("CA du jour",        "#1267C4", BootstrapIcons.CASH_STACK);
        KpiCard sorties  = new KpiCard("Sorties nettes",    "#1A8754", BootstrapIcons.TRUCK);
        KpiCard creances = new KpiCard("Créances en cours", "#E6940C", BootstrapIcons.CLOCK_HISTORY);
        KpiCard bloques  = new KpiCard("Clients bloqués",   "#DC3545", BootstrapIcons.SLASH_CIRCLE);
        KpiCard ecarts   = new KpiCard("Écarts de caisse",  "#DC3545", BootstrapIcons.EXCLAMATION_TRIANGLE_FILL);

        // Stocker les références AVANT d'ajouter dans le HBox
        lblCA       = ca.valLabel;
        lblSorties  = sorties.valLabel;
        lblCreances = creances.valLabel;
        lblBloques  = bloques.valLabel;
        lblEcarts   = ecarts.valLabel;
        lblVarCA    = ca.varLabel;
        lblVarEcarts= ecarts.varLabel;

        for (KpiCard kc : new KpiCard[]{ca, sorties, creances, bloques, ecarts}) {
            HBox.setHgrow(kc.card, Priority.ALWAYS);
            row.getChildren().add(kc.card);
        }
        return row;
    }

    /** Classe interne qui encapsule la carte KPI et expose les labels. */
    private static class KpiCard {
        final VBox  card;
        final Label valLabel;
        final Label varLabel;

        KpiCard(String titre, String color, BootstrapIcons icon) {
            card = new VBox(6);
            card.getStyleClass().add("kpi-card");

            HBox topRow = new HBox(6);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label lblT = new Label(titre);
            lblT.getStyleClass().add("kpi-title");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            FontIcon fi = new FontIcon(icon);
            fi.setIconSize(15);
            fi.setIconColor(Color.web(color));
            topRow.getChildren().addAll(lblT, sp, fi);

            valLabel = new Label("—");
            valLabel.getStyleClass().add("kpi-value");

            varLabel = new Label("");
            varLabel.getStyleClass().add("kpi-variation");

            card.getChildren().addAll(topRow, valLabel, varLabel);
        }
    }

    // ── Ligne centrale 3 colonnes ─────────────────────────────────
    private HBox buildMidRow() {
        HBox row = new HBox(12);
        row.setFillHeight(true);
        VBox.setVgrow(row, Priority.ALWAYS);

        // Raccourcis
        VBox raccourcis = buildRaccourcisCard();
        HBox.setHgrow(raccourcis, Priority.ALWAYS);
        VBox.setVgrow(raccourcis, Priority.ALWAYS);

        // Activité récente
        pnlActivite = new VBox(6);
        pnlActivite.setPadding(new Insets(4, 0, 0, 0));
        ScrollPane scrollAct = new ScrollPane(pnlActivite);
        scrollAct.setFitToWidth(true);
        scrollAct.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        VBox.setVgrow(scrollAct, Priority.ALWAYS);
        VBox actCard = buildCard("Activité récente", scrollAct);
        HBox.setHgrow(actCard, Priority.ALWAYS);
        VBox.setVgrow(actCard, Priority.ALWAYS);

        // Alertes
        pnlAlertes = new VBox(8);
        pnlAlertes.setPadding(new Insets(4, 0, 0, 0));
        ScrollPane scrollAlt = new ScrollPane(pnlAlertes);
        scrollAlt.setFitToWidth(true);
        scrollAlt.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        VBox.setVgrow(scrollAlt, Priority.ALWAYS);
        VBox alertCard = buildCard("Alertes", scrollAlt);
        HBox.setHgrow(alertCard, Priority.ALWAYS);
        VBox.setVgrow(alertCard, Priority.ALWAYS);

        row.getChildren().addAll(raccourcis, actCard, alertCard);
        return row;
    }

    private VBox buildRaccourcisCard() {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(8, 0, 0, 0));

        // Chaque raccourci : image + label
        record Shortcut(String label, String key, String imgPath, BootstrapIcons fallback) {}
        var items = new Shortcut[]{
            new Shortcut("Produits",     MainWindow.PRODUITS,     "assets/Image 1 1.png", BootstrapIcons.BOX_SEAM),
            new Shortcut("Clients",      MainWindow.CLIENTS,      "assets/Image 3 1.png", BootstrapIcons.PEOPLE),
            new Shortcut("Sorties",      MainWindow.SORTIES,      "assets/Image 1.png",   BootstrapIcons.TRUCK),
            new Shortcut("Facturation",  MainWindow.FACTURATION,  "assets/Image 1 3.png", BootstrapIcons.RECEIPT),
            new Shortcut("Caisse",       MainWindow.CAISSE,       "assets/Image 1 4.png", BootstrapIcons.CASH_STACK),
            new Shortcut("Recouvrement", MainWindow.RECOUVREMENT, "assets/Image 1 5.png", BootstrapIcons.BAR_CHART_LINE),
        };

        for (int i = 0; i < items.length; i++) {
            var it = items[i];
            javafx.scene.Node graphic;
            var imgView = loadImage(it.imgPath(), 32, 32);
            if (imgView != null) {
                graphic = imgView;
            } else {
                FontIcon icon = new FontIcon(it.fallback());
                icon.setIconSize(24);
                icon.getStyleClass().add("shortcut-icon");
                graphic = icon;
            }
            Button btn = new Button(it.label(), graphic);
            btn.getStyleClass().add("shortcut-button");
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> mainWindow.navigate(it.key()));
            GridPane.setHgrow(btn, Priority.ALWAYS);
            grid.add(btn, i % 3, i / 3);
        }
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < 2; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS); rc.setFillHeight(true);
            rc.setMinHeight(80);
            grid.getRowConstraints().add(rc);
        }

        VBox card = buildCard("Raccourcis rapides", grid);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return card;
    }

    private VBox buildCard(String titre, javafx.scene.Node content) {
        Label lbl = new Label(titre);
        lbl.getStyleClass().add("subsection-title");
        Region sep = new Region();
        sep.getStyleClass().add("section-divider");
        sep.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(8, lbl, sep, content);
        card.getStyleClass().add("dashboard-card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    /** Charge une image depuis le classpath */
    private javafx.scene.image.ImageView loadImage(String path, double w, double h) {
        try {
            var url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            var img = new javafx.scene.image.Image(url.toExternalForm(), w, h, true, true);
            var iv  = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) { return null; }
    }

    // ── Refresh données ───────────────────────────────────────────
    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        Platform.runLater(() -> {
            if (lblCA != null)       lblCA.setText("…");
            if (lblSorties != null)  lblSorties.setText("…");
            if (lblCreances != null) lblCreances.setText("…");
            if (lblBloques != null)  lblBloques.setText("…");
            if (lblEcarts != null)   lblEcarts.setText("…");
        });

        runAsync(() -> {
            var d = new DashData();
            try {
                d.ca        = factureDAO.getCaJour(today);
                d.sorties   = ficheDAO.getSortiesNettesJour(today);
                d.creances  = factureDAO.getCreancesEnCours();
                d.bloques   = clientDAO.countBloques();
                d.ecarts    = versementDAO.getEcartsCaisseJour(today);
                d.activites = auditDAO.search(null, null, null, today, today, 8, 0);
            } catch (Exception e) {
                // Ne pas planter le dashboard si une requête échoue
                d.ca = d.sorties = d.creances = d.ecarts = BigDecimal.ZERO;
            }
            return d;
        }, d -> {
            if (lblCA != null)       lblCA.setText(FormatUtil.montant(d.ca) + " FCFA");
            if (lblSorties != null)  lblSorties.setText(FormatUtil.montant(d.sorties) + " FCFA");
            if (lblCreances != null) lblCreances.setText(FormatUtil.montant(d.creances) + " FCFA");
            if (lblBloques != null)  lblBloques.setText(d.bloques + " client(s)");
            if (lblEcarts != null)   lblEcarts.setText(FormatUtil.montant(d.ecarts) + " FCFA");

            if (lblVarCA != null)    lblVarCA.setText("Aujourd'hui");
            if (lblVarEcarts != null) {
                boolean ok = d.ecarts.compareTo(BigDecimal.ZERO) == 0;
                lblVarEcarts.setText(ok ? "✓ OK" : "⚠ À régulariser");
                lblVarEcarts.setStyle("-fx-font-size:11px; -fx-text-fill:"
                    + (ok ? "#1A8754" : "#DC3545") + ";");
            }

            if (pnlActivite != null) refreshActivites(d.activites);
            if (pnlAlertes  != null) refreshAlertes(d);
        });
    }

    private void refreshActivites(List<JournalAudit> list) {
        pnlActivite.getChildren().clear();
        if (list == null || list.isEmpty()) {
            Label empty = new Label("Aucune activité aujourd'hui.");
            empty.setStyle("-fx-font-size:12px; -fx-text-fill:#9AA0A6;");
            pnlActivite.getChildren().add(empty);
            return;
        }
        for (JournalAudit a : list) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 0, 5, 0));
            row.setStyle("-fx-border-color:transparent transparent #EEF2F7 transparent; -fx-border-width:0 0 1 0;");

            String iconColor = switch (a.getAction() != null ? a.getAction() : "") {
                case "CREATE"  -> "#1A8754";
                case "DELETE", "BLOCK", "ECART" -> "#DC3545";
                case "UPDATE"  -> "#E6940C";
                case "LOGIN"   -> "#1267C4";
                case "UNBLOCK" -> "#7B2FBE";
                default        -> "#9AA0A6";
            };

            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
            dot.setFill(Color.web(iconColor));

            VBox texts = new VBox(1);
            Label action = new Label((a.getEntite() != null ? a.getEntite() : "?")
                + "  —  " + (a.getAction() != null ? a.getAction() : "?"));
            action.setStyle("-fx-font-weight:bold; -fx-font-size:12px; -fx-text-fill:#1A2942;");
            String det = a.getDetails() != null && a.getDetails().length() > 55
                ? a.getDetails().substring(0, 55) + "…" : (a.getDetails() != null ? a.getDetails() : "");
            Label details = new Label(det);
            details.setStyle("-fx-font-size:11px; -fx-text-fill:#546E7A;");
            texts.getChildren().addAll(action, details);
            HBox.setHgrow(texts, Priority.ALWAYS);

            String h = a.getDateAction() != null
                ? a.getDateAction().format(DateTimeFormatter.ofPattern("HH:mm")) : "";
            Label heure = new Label(h);
            heure.setStyle("-fx-font-size:11px; -fx-text-fill:#9AA0A6;");

            row.getChildren().addAll(dot, texts, heure);
            pnlActivite.getChildren().add(row);
        }
    }

    private void refreshAlertes(DashData d) {
        pnlAlertes.getChildren().clear();
        if (d.bloques > 0)
            pnlAlertes.getChildren().add(alertRow("#DC3545","#FDE8EA",
                "🔴  " + d.bloques + " client(s) bloqué(s)", "Voir →", MainWindow.CLIENTS));
        if (d.creances != null && d.creances.compareTo(BigDecimal.ZERO) > 0)
            pnlAlertes.getChildren().add(alertRow("#E6940C","#FFF8E1",
                "🟡  Créances : " + FormatUtil.montant(d.creances) + " FCFA", "Détail →", MainWindow.FACTURATION));
        if (d.ecarts != null && d.ecarts.compareTo(BigDecimal.ZERO) < 0)
            pnlAlertes.getChildren().add(alertRow("#DC3545","#FDE8EA",
                "🔴  Écart caisse : " + FormatUtil.montant(d.ecarts) + " FCFA", "Rapproch. →", MainWindow.RECOUVREMENT));

        if (pnlAlertes.getChildren().isEmpty()) {
            Label ok = new Label("✅  Aucune alerte active");
            ok.setStyle("-fx-text-fill:#1A8754; -fx-font-size:13px; -fx-padding:8 0;");
            pnlAlertes.getChildren().add(ok);
        }
    }

    private HBox alertRow(String border, String bg, String msg, String link, String dest) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 8, 8, 10));
        row.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:6; "
            + "-fx-border-color:transparent transparent transparent " + border
            + "; -fx-border-width:0 0 0 3; -fx-border-radius:0 6 6 0;");
        Label lbl = new Label(msg);
        lbl.setStyle("-fx-font-size:12px; -fx-text-fill:#1A2942;");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        Hyperlink hl = new Hyperlink(link);
        hl.setStyle("-fx-font-size:11px; -fx-text-fill:" + border + ";");
        hl.setOnAction(e -> mainWindow.navigate(dest));
        row.getChildren().addAll(lbl, hl);
        return row;
    }

    private static class DashData {
        BigDecimal ca = BigDecimal.ZERO, sorties = BigDecimal.ZERO,
                   creances = BigDecimal.ZERO, ecarts = BigDecimal.ZERO;
        int bloques = 0;
        List<JournalAudit> activites = List.of();
    }
}
