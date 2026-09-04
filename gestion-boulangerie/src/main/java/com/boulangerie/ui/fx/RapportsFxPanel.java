package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.PdfService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Module Rapports & Exports PDF — Planche 3.
 */
public class RapportsFxPanel extends FxPanelBase {

    private final FactureDAO   factureDAO   = new FactureDAO();
    private final ClientDAO    clientDAO    = new ClientDAO();
    private final VersementDAO versementDAO = new VersementDAO();
    private final AuditDAO     auditDAO     = new AuditDAO();
    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();

    public RapportsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        Label lblSub = new Label(
            "Sélectionnez un rapport pour le générer au format PDF (A4).");
        lblSub.setStyle("-fx-font-size:13px; -fx-text-fill:#5F6368;");

        // Grille de rapports 2×3
        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(16);
        grid.setPadding(new Insets(8, 0, 0, 0));

        String[][] rapports = {
            {"📋", "État journalier",
                "Sorties/retours du jour avec totaux par livreur.", "JOURNALIER"},
            {"🧾", "Factures du mois",
                "Liste des factures émises sur le mois courant.", "FACTURES"},
            {"💰", "Recouvrement mensuel",
                "Objectif vs réalisé, taux de recouvrement.", "RECOUVREMENT"},
            {"👥", "Soldes clients",
                "État des soldes de tous les clients nominatifs.", "SOLDES"},
            {"📦", "Analyse produits",
                "Volumes vendus par produit et par famille.", "PRODUITS"},
            {"🔍", "Journal d'audit",
                "Export du journal d'audit des 30 derniers jours.", "AUDIT"},
        };

        for (int i = 0; i < rapports.length; i++) {
            String[] r = rapports[i];
            VBox card = rapportCard(r[0], r[1], r[2], r[3]);
            grid.add(card, i % 3, i / 3);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setVgrow(card, Priority.ALWAYS);
        }
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }

        Label lblNote = new Label(
            "Tous les rapports sont générés au format PDF A4 avec en-tête entreprise, "
            + "numéro de page et date de génération.");
        lblNote.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");

        VBox body = new VBox(12,
            header("Rapports & Exports"),
            lblSub, grid, lblNote);
        body.setFillWidth(true);
        root.setCenter(body);
    }

    private VBox rapportCard(String icon, String titre, String desc, String type) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:16; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,1);");

        Label lblIcon  = new Label(icon);
        lblIcon.setStyle("-fx-font-size:28px;");
        Label lblTitre = new Label(titre);
        lblTitre.setStyle("-fx-font-size:14px; -fx-font-weight:bold;");
        Label lblDesc  = new Label(desc);
        lblDesc.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368; -fx-wrap-text:true;");
        lblDesc.setMaxWidth(Double.MAX_VALUE);

        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER_LEFT);
        Button btnPDF     = btnPrimary("Export PDF", null);
        Button btnAperçu  = btnOutline("Aperçu");
        btnPDF.setOnAction(e    -> genererPDF(type));
        btnAperçu.setOnAction(e -> genererPDF(type));
        btns.getChildren().addAll(btnPDF, btnAperçu);

        card.getChildren().addAll(lblIcon, lblTitre, lblDesc, btns);
        VBox.setVgrow(lblDesc, Priority.ALWAYS);
        return card;
    }

    private void genererPDF(String type) {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(type.toLowerCase() + "_" + LocalDate.now() + ".pdf");
        File file = fc.showSaveDialog(mainWindow.getStage());
        if (file == null) return;

        runAsync(() -> {
            switch (type) {
                case "JOURNALIER" -> {
                    List<FicheJournaliere> fiches = ficheDAO.findByDate(LocalDate.now());
                    PdfService.exporterEtatJournalier(fiches, file.getAbsolutePath());
                }
                case "FACTURES" -> {
                    LocalDate debut = LocalDate.now().withDayOfMonth(1);
                    List<Facture> factures = factureDAO.findByFilters(debut, LocalDate.now(), null, null);
                    PdfService.exporterListeFactures(factures, file.getAbsolutePath());
                }
                case "RECOUVREMENT" -> {
                    PdfService.exporterRecouvrement(versementDAO,
                        LocalDate.now().withDayOfMonth(1), LocalDate.now(), file.getAbsolutePath());
                }
                case "SOLDES" -> {
                    List<Client> clients = clientDAO.findAll();
                    PdfService.exporterSoldesClients(clients, file.getAbsolutePath());
                }
                case "AUDIT" -> {
                    var audits = auditDAO.search(null, null, null,
                        LocalDate.now().minusDays(30), LocalDate.now(), 500, 0);
                    PdfService.exporterAudit(audits, file.getAbsolutePath());
                }
                default -> throw new UnsupportedOperationException("Type: " + type);
            }
            return true;
        }, ok -> mainWindow.showAlert("Export PDF",
            "Rapport généré : " + file.getName(), Alert.AlertType.INFORMATION));
    }

    @Override public void refresh() {}
}
