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
import javafx.scene.text.Text;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard Administrateur — chargement lazy + données en background thread.
 */
public class DashboardFxPanel extends FxPanelBase {

    private final FactureDAO          factureDAO   = new FactureDAO();
    private final ClientDAO           clientDAO    = new ClientDAO();
    private final FicheJournaliereDAO ficheDAO     = new FicheJournaliereDAO();
    private final VersementDAO        versementDAO = new VersementDAO();
    private final AuditDAO            auditDAO     = new AuditDAO();

    // KPI
    private Label lblCA, lblSorties, lblCreances, lblBloques, lblEcarts;
    private Label lblVarCA, lblVarEcarts;
    // Activité + Alertes
    private VBox  pnlActivite;
    private VBox  pnlAlertes;

    public DashboardFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        // ── Titre + date ──────────────────────────────────────────
        Label titre = sectionTitle("Tableau de bord");
        String dateStr = LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",
                java.util.Locale.FRENCH));
        dateStr = dateStr.substring(0,1).toUpperCase() + dateStr.substring(1);
        Label lblDate = new Label(dateStr);
        lblDate.setStyle("-fx-font-size:12px; -fx-text-fill:#5F6368;");
        VBox titleBox = new VBox(2, titre, lblDate);
        titleBox.setPadding(new Insets(0, 0, 14, 0));

        // ── Ligne KPIs ────────────────────────────────────────────
        HBox kpiRow = buildKpiRow();

        // ── Ligne centrale 3 colonnes ─────────────────────────────
        HBox midRow = buildMidRow();
        VBox.setVgrow(midRow, Priority.ALWAYS);

        VBox body = new VBox(14, titleBox, kpiRow, midRow);
        body.setFillWidth(true);
        root.setCenter(body);
    }

    // ── KPI Row ───────────────────────────────────────────────────
    private HBox buildKpiRow() {
        HBox row = new HBox(12);
        row.setFillHeight(false);

        // 5 KPI cards
        var ca       = makeKpiCard("CA du jour",        "Chargement…", "#1A73E8");
        var sorties  = makeKpiCard("Sorties nettes",    "Chargement…", "#0F9D58");
        var creances = makeKpiCard("Créances en cours", "Chargement…", "#F29900");
        var bloques  = makeKpiCard("Clients bloqués",   "Chargement…", "#D93025");
        var ecarts   = makeKpiCard("Écarts de caisse",  "Chargement…", "#D93025");

        lblCA       = (Label) ((VBox) ca      ).getChildren().get(1);
        lblSorties  = (Label) ((VBox) sorties ).getChildren().get(1);
        lblCreances = (Label) ((VBox) creances).getChildren().get(1);
        lblBloques  = (Label) ((VBox) bloques ).getChildren().get(1);
        lblEcarts   = (Label) ((VBox) ecarts  ).getChildren().get(1);
        lblVarCA    = (Label) ((VBox) ca      ).getChildren().get(2);
        lblVarEcarts= (Label) ((VBox) ecarts  ).getChildren().get(2);

        for (VBox card : new VBox[]{ca, sorties, creances, bloques, ecarts}) {
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }
        return row;
    }

    private VBox makeKpiCard(String titre, String val, String color) {
        VBox card = new VBox(4);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16 14 16; "
            + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.06),8,0,0,2);");

        HBox topRow = new HBox(4);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label lblT = new Label(titre);
        lblT.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Text dot = new Text("●");
        dot.setStyle("-fx-fill:" + color + "; -fx-font-size:10px;");
        topRow.getChildren().addAll(lblT, sp, dot);

        Label lblV = new Label(val);
        lblV.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:#202124;");

        Label lblVar = new Label("");
        lblVar.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368;");

        card.getChildren().addAll(topRow, lblV, lblVar);
        return card;
    }

    // ── Ligne centrale ────────────────────────────────────────────
    private HBox buildMidRow() {
        HBox row = new HBox(12);
        row.setFillHeight(false);
        row.setPrefHeight(350);
        row.setMaxHeight(350);

        // Col 1 : Raccourcis
        VBox raccourcis = buildRaccourcisCard();
        HBox.setHgrow(raccourcis, Priority.ALWAYS);

        // Col 2 : Activité récente
        pnlActivite = new VBox(6);
        pnlActivite.setPadding(new Insets(4, 0, 0, 0));
        ScrollPane scrollAct = new ScrollPane(pnlActivite);
        scrollAct.setFitToWidth(true);
        scrollAct.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        VBox.setVgrow(scrollAct, Priority.ALWAYS);
        VBox actCard = cardContainer("Activité récente", scrollAct);
        HBox.setHgrow(actCard, Priority.ALWAYS);
        actCard.setPrefHeight(350);

        // Col 3 : Alertes
        pnlAlertes = new VBox(8);
        pnlAlertes.setPadding(new Insets(4, 0, 0, 0));
        ScrollPane scrollAlt = new ScrollPane(pnlAlertes);
        scrollAlt.setFitToWidth(true);
        scrollAlt.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");
        VBox.setVgrow(scrollAlt, Priority.ALWAYS);
        Button btnVoir = new Button("Voir tous les clients →");
        btnVoir.setStyle("-fx-background-color:transparent; -fx-text-fill:#1A73E8; "
            + "-fx-cursor:hand; -fx-font-size:12px; -fx-border-width:0;");
        btnVoir.setOnAction(e -> mainWindow.navigate(MainWindow.CLIENTS));
        VBox alertCard = cardContainer("Alertes", scrollAlt);
        alertCard.getChildren().add(btnVoir);
        HBox.setHgrow(alertCard, Priority.ALWAYS);
        alertCard.setPrefHeight(350);

        row.getChildren().addAll(raccourcis, actCard, alertCard);
        return row;
    }

    private VBox buildRaccourcisCard() {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(8, 0, 0, 0));

        Shortcut[] items = {
            new Shortcut("Produits", MainWindow.PRODUITS, BootstrapIcons.BOX_SEAM),
            new Shortcut("Clients", MainWindow.CLIENTS, BootstrapIcons.PEOPLE),
            new Shortcut("Sorties", MainWindow.SORTIES, BootstrapIcons.TRUCK),
            new Shortcut("Facturation", MainWindow.FACTURATION, BootstrapIcons.RECEIPT),
            new Shortcut("Caisse", MainWindow.CAISSE, BootstrapIcons.CASH_STACK),
            new Shortcut("Recouvrement", MainWindow.RECOUVREMENT, BootstrapIcons.BAR_CHART_LINE),
        };

        for (int i = 0; i < items.length; i++) {
            Shortcut it = items[i];
            FontIcon icon = new FontIcon(it.icon());
            icon.getStyleClass().add("shortcut-icon");
            Button btn = new Button(it.label(), icon);
            btn.getStyleClass().add("shortcut-button");
            btn.setContentDisplay(ContentDisplay.TOP);
            btn.setMaxSize(Double.MAX_VALUE, 88);
            btn.setOnAction(e -> mainWindow.navigate(it.key()));
            GridPane.setHgrow(btn, Priority.ALWAYS);
            GridPane.setVgrow(btn, Priority.NEVER);
            grid.add(btn, i % 3, i / 3);
        }
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < 2; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.NEVER); rc.setFillHeight(true);
            rc.setMinHeight(88); rc.setPrefHeight(88);
            grid.getRowConstraints().add(rc);
        }

        VBox card = cardContainer("Raccourcis rapides", grid);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setPrefHeight(350);
        return card;
    }

    private VBox cardContainer(String titre, javafx.scene.Node content) {
        Label lbl = new Label(titre);
        lbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#202124;");
        Region sep = new Region();
        sep.getStyleClass().add("section-divider");

        VBox card = new VBox(8, lbl, sep, content);
        card.getStyleClass().add("dashboard-card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    private record Shortcut(String label, String key, BootstrapIcons icon) { }

    // ── Refresh (données en background) ──────────────────────────
    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();

        // Afficher "Chargement…" immédiatement
        Platform.runLater(() -> {
            lblCA.setText("Chargement…");
            lblSorties.setText("Chargement…");
            lblCreances.setText("Chargement…");
            lblBloques.setText("Chargement…");
            lblEcarts.setText("Chargement…");
        });

        // Charger en background puis mettre à jour l'UI
        runAsync(() -> {
            var d = new DashData();
            d.ca        = factureDAO.getCaJour(today);
            d.sorties   = ficheDAO.getSortiesNettesJour(today);
            d.creances  = factureDAO.getCreancesEnCours();
            d.bloques   = clientDAO.countBloques();
            d.ecarts    = versementDAO.getEcartsCaisseJour(today);
            d.activites = auditDAO.search(null,null,null,today,today,8,0);
            return d;
        }, d -> {
            lblCA.setText(FormatUtil.montant(d.ca) + " FCFA");
            lblVarCA.setText("Aujourd'hui");

            lblSorties.setText(FormatUtil.montant(d.sorties) + " FCFA");
            lblCreances.setText(FormatUtil.montant(d.creances) + " FCFA");
            lblBloques.setText(d.bloques + " client(s)");
            lblEcarts.setText(FormatUtil.montant(d.ecarts) + " FCFA");

            boolean ecartOk = d.ecarts.compareTo(BigDecimal.ZERO) == 0;
            lblVarEcarts.setText(ecartOk ? "✓ OK" : "⚠ À régulariser");
            lblVarEcarts.setStyle("-fx-font-size:11px; -fx-text-fill:"
                + (ecartOk ? "#0F9D58" : "#D93025") + ";");

            refreshActivites(d.activites);
            refreshAlertes(d);
        });
    }

    private void refreshActivites(List<JournalAudit> list) {
        pnlActivite.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label("Aucune activité aujourd'hui.");
            empty.setStyle("-fx-font-size:12px; -fx-text-fill:#9AA0A6;");
            pnlActivite.getChildren().add(empty);
            return;
        }
        for (JournalAudit a : list) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 0, 5, 0));
            row.setStyle("-fx-border-color:transparent transparent #F4F6FA transparent; "
                + "-fx-border-width:0 0 1 0;");

            // Icône action
            String iconColor = switch (a.getAction()) {
                case "CREATE" -> "#0F9D58";
                case "DELETE", "BLOCK", "ECART" -> "#D93025";
                case "UPDATE" -> "#F29900";
                case "LOGIN"  -> "#1A73E8";
                default -> "#9AA0A6";
            };
            Text dot = new Text("●");
            dot.setStyle("-fx-fill:" + iconColor + "; -fx-font-size:8px;");

            VBox texts = new VBox(1);
            Label action = new Label(a.getEntite() + "  —  " + a.getAction());
            action.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
            String det = a.getDetails() != null && a.getDetails().length() > 55
                ? a.getDetails().substring(0, 55) + "…"
                : (a.getDetails() != null ? a.getDetails() : "");
            Label details = new Label(det);
            details.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368;");
            texts.getChildren().addAll(action, details);
            HBox.setHgrow(texts, Priority.ALWAYS);

            String h = a.getDateAction() != null
                ? a.getDateAction().format(
                    DateTimeFormatter.ofPattern("HH:mm")) : "";
            Label heure = new Label(h);
            heure.setStyle("-fx-font-size:11px; -fx-text-fill:#9AA0A6;");

            row.getChildren().addAll(dot, texts, heure);
            pnlActivite.getChildren().add(row);
        }
    }

    private void refreshAlertes(DashData d) {
        pnlAlertes.getChildren().clear();

        if (d.bloques > 0) {
            pnlAlertes.getChildren().add(alertRow(
                "#D93025", "#FCE8E6",
                "🔴  " + d.bloques + " client(s) bloqué(s)",
                "Voir →", MainWindow.CLIENTS));
        }
        if (d.creances.compareTo(BigDecimal.ZERO) > 0) {
            pnlAlertes.getChildren().add(alertRow(
                "#F29900", "#FEF7E0",
                "🟡  Créances : " + FormatUtil.montant(d.creances) + " FCFA",
                "Détail →", MainWindow.FACTURATION));
        }
        if (d.ecarts.compareTo(BigDecimal.ZERO) < 0) {
            pnlAlertes.getChildren().add(alertRow(
                "#D93025", "#FCE8E6",
                "🔴  Écart caisse : " + FormatUtil.montant(d.ecarts) + " FCFA",
                "Rapproch. →", MainWindow.RECOUVREMENT));
        }

        if (pnlAlertes.getChildren().isEmpty()) {
            Label ok = new Label("✅  Aucune alerte active");
            ok.setStyle("-fx-text-fill:#0F9D58; -fx-font-size:13px; -fx-padding:8;");
            pnlAlertes.getChildren().add(ok);
        }
    }

    private HBox alertRow(String borderColor, String bgColor,
                          String msg, String link, String dest) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 8, 8, 10));
        row.setStyle(
            "-fx-background-color:" + bgColor + "; "
            + "-fx-background-radius:6; "
            + "-fx-border-color:transparent transparent transparent " + borderColor + "; "
            + "-fx-border-width:0 0 0 3; -fx-border-radius:0 6 6 0;");

        Label lbl = new Label(msg);
        lbl.setStyle("-fx-font-size:12px; -fx-text-fill:#202124;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        Hyperlink hl = new Hyperlink(link);
        hl.setStyle("-fx-font-size:11px; -fx-text-fill:" + borderColor + ";");
        hl.setOnAction(e -> mainWindow.navigate(dest));

        row.getChildren().addAll(lbl, hl);
        return row;
    }

    private static class DashData {
        BigDecimal ca, sorties, creances, ecarts;
        int bloques;
        List<JournalAudit> activites;
    }
}
