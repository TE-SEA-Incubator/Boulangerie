package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.ExcelExportService;
import com.boulangerie.service.PdfService;
import com.boulangerie.service.PrevisionService;
import com.boulangerie.service.PrevisionService.PrevisionProduit;
import com.boulangerie.service.PrevisionService.SynthesePrevision;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Module Rapports, Analyses & Prévisions — Interface dense, attrayante
 * avec moteur prévisionnel de production/chiffre d'affaires et aperçu interactif en direct.
 */
public class RapportsFxPanel extends FxPanelBase {

    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final VersementDAO versementDAO = new VersementDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final AuditDAO auditDAO = new AuditDAO();
    private final CaisseService caisseService = new CaisseService();
    private final PrevisionService previsionService = new PrevisionService();

    // Onglet Prévisions
    private Label lblCa7Jours, lblCaMois, lblPiecesDemain, lblEncaissement7J, lblMoyJour;
    private TableView<PrevisionProduit> tablePrevisions;
    private ObservableList<PrevisionProduit> dataPrevisions = FXCollections.observableArrayList();

    // Onglet Aperçu Rapports
    private ComboBox<String> cboTypeRapport;
    private DatePicker dpDateRapport;
    private TableView<ObservableList<String>> tableApercu;
    private ObservableList<ObservableList<String>> dataApercu = FXCollections.observableArrayList();
    private Label lblApercuResume;

    public RapportsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: transparent;");

        Tab tabPrevisions = new Tab("📈 Prévisions de Ventes & Production", buildOngletPrevisions());
        Tab tabApercu = new Tab("📋 Visualisation & Exports de Rapports", buildOngletApercu());

        tabPane.getTabs().addAll(tabPrevisions, tabApercu);

        VBox body = new VBox(10,
            header("Rapports, Analyses & Prévisions"),
            tabPane
        );
        body.setFillWidth(true);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root.setCenter(body);
    }

    // ── 1. Onglet Prévisions ─────────────────────────────────────
    private VBox buildOngletPrevisions() {
        // Cartes KPI de prévisions
        lblCa7Jours = kpiCard("CA Prévisionnel (7 jours)", "0 FCFA", "#1F3A5F");
        lblCaMois = kpiCard("CA Projeté (Fin de mois)", "0 FCFA", "#2E5A88");
        lblPiecesDemain = kpiCard("Production requise Demain", "0 pièces", "#F5A623");
        lblEncaissement7J = kpiCard("Encaissements attendus (7j)", "0 FCFA", "#2E7D32");
        lblMoyJour = kpiCard("Moyenne quotidienne actuelle", "0 FCFA / jour", "#5F6368");

        HBox kpiBanner = new HBox(10, lblCa7Jours, lblCaMois, lblPiecesDemain, lblEncaissement7J, lblMoyJour);
        kpiBanner.setPadding(new Insets(10, 0, 10, 0));

        // Table des prévisions par produit
        tablePrevisions = styledTable();
        tablePrevisions.setItems(dataPrevisions);

        TableColumn<PrevisionProduit, String> colProduit = new TableColumn<>("Produit / Type de pain");
        TableColumn<PrevisionProduit, String> colPrix = new TableColumn<>("Prix unit.");
        TableColumn<PrevisionProduit, String> colMoyJour = new TableColumn<>("Moyenne vendue/j");
        TableColumn<PrevisionProduit, String> colDemain = new TableColumn<>("Prévision Demain (J+1)");
        TableColumn<PrevisionProduit, String> colSemaine = new TableColumn<>("Prévision 7 Jours");
        TableColumn<PrevisionProduit, String> colCaEstime = new TableColumn<>("CA Estimé Demain");
        TableColumn<PrevisionProduit, String> colReco = new TableColumn<>("Recommandation Fournée");

        colProduit.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().designation()));
        colProduit.setPrefWidth(220);

        colPrix.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().prixUnitaire())));
        colPrix.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPrix.setPrefWidth(100);

        colMoyJour.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().moyenneJour() + " pcs"));
        colMoyJour.setStyle("-fx-alignment: CENTER-RIGHT;");
        colMoyJour.setPrefWidth(120);

        colDemain.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().previDemain() + " pcs"));
        colDemain.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E5A88;");
        colDemain.setPrefWidth(140);

        colSemaine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().previSemaine() + " pcs"));
        colSemaine.setStyle("-fx-alignment: CENTER-RIGHT;");
        colSemaine.setPrefWidth(120);

        colCaEstime.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().caEstimeDemain()) + " FCFA"));
        colCaEstime.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        colCaEstime.setPrefWidth(130);

        colReco.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().recommandation()));
        colReco.setPrefWidth(300);

        tablePrevisions.getColumns().addAll(colProduit, colPrix, colMoyJour, colDemain, colSemaine, colCaEstime, colReco);

        // Barre d'outils
        Button btnRecalculer = btnPrimary("⟳ Actualiser les Prévisions", BootstrapIcons.ARROW_REPEAT);
        btnRecalculer.setOnAction(e -> chargerPrevisions());

        Label lblConseil = new Label("💡 Conseil d'optimisation : Les estimations intègrent une marge de régularité de 5% pour éviter les ruptures matinales tout en minimisant les retours invendus.");
        lblConseil.setStyle("-fx-font-size: 11px; -fx-text-fill: #5F6368;");

        HBox topBar = new HBox(12, btnRecalculer, lblConseil);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, kpiBanner, topBar, tablePrevisions);
        box.setPadding(new Insets(10, 0, 0, 0));
        VBox.setVgrow(tablePrevisions, Priority.ALWAYS);
        return box;
    }

    // ── 2. Onglet Aperçu & Exports ──────────────────────────────
    private VBox buildOngletApercu() {
        cboTypeRapport = new ComboBox<>(FXCollections.observableArrayList(
            "Fiche Journalière des Sorties & Retours",
            "Feuille de Facturation & Caisse Journalière",
            "Grand Livre des Soldes Clients",
            "Historique des Versements du Jour",
            "Journal d'Audit & Sécurité"
        ));
        cboTypeRapport.setValue("Fiche Journalière des Sorties & Retours");
        cboTypeRapport.setPrefWidth(280);
        cboTypeRapport.setOnAction(e -> actualiserApercu());

        dpDateRapport = new DatePicker(LocalDate.now());
        dpDateRapport.setPrefWidth(140);
        dpDateRapport.setOnAction(e -> actualiserApercu());

        Button btnPDF = btnPrimary("📄 Exporter PDF (A4)", BootstrapIcons.FILE_TEXT);
        Button btnExcel = btnOutline("📊 Exporter Excel (.csv)");
        Button btnRefresh = btnOutline("⟳ Actualiser");

        btnPDF.setOnAction(e -> exporterRapportCourantPDF());
        btnExcel.setOnAction(e -> exporterRapportCourantExcel());
        btnRefresh.setOnAction(e -> actualiserApercu());

        HBox toolbar = new HBox(10,
            new Label("Rapport :"), cboTypeRapport,
            new Label("Date :"), dpDateRapport,
            btnPDF, btnExcel, btnRefresh
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 0, 10, 0));

        lblApercuResume = new Label("Chargement de l'aperçu...");
        lblApercuResume.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");

        tableApercu = styledTable();

        VBox box = new VBox(8, toolbar, lblApercuResume, tableApercu);
        box.setPadding(new Insets(10, 0, 0, 0));
        VBox.setVgrow(tableApercu, Priority.ALWAYS);
        return box;
    }

    private Label kpiCard(String titre, String val, String col) {
        Label lbl = new Label(titre + "\n" + val);
        lbl.setStyle("-fx-background-color: white; -fx-border-color: #D6CFC4; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 10 14; -fx-font-size: 11px; -fx-text-fill: #5F6368; "
            + "-fx-line-spacing: 4px;");
        lbl.setPrefWidth(190);
        return lbl;
    }

    @Override
    public void refresh() {
        chargerPrevisions();
        actualiserApercu();
    }

    private void chargerPrevisions() {
        runAsync(previsionService::genererPrevisions, synthese -> {
            lblCa7Jours.setText("CA Prévisionnel (7 jours)\n" + FormatUtil.montant(synthese.caPrevu7Jours()) + " FCFA");
            lblCaMois.setText("CA Projeté (Fin de mois)\n" + FormatUtil.montant(synthese.caPrevuFinMois()) + " FCFA");
            lblPiecesDemain.setText("Production requise Demain\n" + synthese.totalPiecesPrevuesDemain() + " pièces");
            lblEncaissement7J.setText("Encaissements attendus (7j)\n" + FormatUtil.montant(synthese.encaissementsEstimes7Jours()) + " FCFA");
            lblMoyJour.setText("Moyenne quotidienne actuelle\n" + FormatUtil.montant(synthese.caMoyenJour()) + " FCFA / jour");

            dataPrevisions.setAll(synthese.previsionsProduits());
        });
    }

    private void actualiserApercu() {
        String type = cboTypeRapport.getValue();
        LocalDate date = dpDateRapport.getValue() != null ? dpDateRapport.getValue() : LocalDate.now();

        tableApercu.getColumns().clear();
        dataApercu.clear();

        runAsync(() -> {
            if ("Fiche Journalière des Sorties & Retours".equals(type)) {
                List<LigneCommande> sorties = ficheDAO.findLignesByDate(date);
                return new Object[]{"SORTIES", sorties};
            } else if ("Feuille de Facturation & Caisse Journalière".equals(type)) {
                List<FicheCaisseLigne> caisse = caisseService.chargerFicheCaisseJournaliere(date);
                return new Object[]{"CAISSE", caisse};
            } else if ("Grand Livre des Soldes Clients".equals(type)) {
                List<Client> clients = clientDAO.findAll();
                return new Object[]{"SOLDES", clients};
            } else if ("Historique des Versements du Jour".equals(type)) {
                List<Versement> versements = versementDAO.findByDate(date);
                return new Object[]{"VERSEMENTS", versements};
            } else {
                var audits = auditDAO.search(null, null, null, date.minusDays(7), date, 200, 0);
                return new Object[]{"AUDIT", audits};
            }
        }, res -> {
            Object[] data = (Object[]) res;
            String t = (String) data[0];
            configurerColonnesApercu(t, data[1]);
        });
    }

    @SuppressWarnings("unchecked")
    private void configurerColonnesApercu(String type, Object dataObj) {
        switch (type) {
            case "SORTIES" -> {
                List<LigneCommande> sorties = (List<LigneCommande>) dataObj;
                ajouterColonnesApercu("N°", "Client / Livreur", "Produit", "Sorties", "Retours", "Net", "Total HT");
                int idx = 1;
                BigDecimal total = BigDecimal.ZERO;
                for (LigneCommande s : sorties) {
                    dataApercu.add(FXCollections.observableArrayList(
                        String.valueOf(idx++),
                        s.getClient() != null ? s.getClient().getNom() : "—",
                        s.getProduit() != null ? s.getProduit().getLibelle() : "—",
                        String.valueOf(s.getQuantiteSortie()),
                        String.valueOf(s.getQuantiteRetournee()),
                        String.valueOf(s.getQuantiteNette()),
                        FormatUtil.montant(s.getMontantHt()) + " FCFA"
                    ));
                    if (s.getMontantHt() != null) total = total.add(s.getMontantHt());
                }
                lblApercuResume.setText("Aperçu : " + sorties.size() + " ligne(s) de sortie — Total HT : " + FormatUtil.montant(total) + " FCFA");
            }
            case "CAISSE" -> {
                List<FicheCaisseLigne> lignes = (List<FicheCaisseLigne>) dataObj;
                ajouterColonnesApercu("N°", "Client / Livreur", "Détail Sorties", "Facture Jour", "Écart Précédent", "Total Solde", "Reçu", "Reste / Écart", "Statut");
                int idx = 1;
                BigDecimal totAtt = BigDecimal.ZERO, totRecu = BigDecimal.ZERO, totReste = BigDecimal.ZERO;
                for (FicheCaisseLigne c : lignes) {
                    dataApercu.add(FXCollections.observableArrayList(
                        String.valueOf(idx++),
                        c.getClient() != null ? c.getClient().getNom() : "—",
                        c.getResumeSorties().isEmpty() ? "—" : c.getResumeSorties(),
                        FormatUtil.montant(c.getMontantFacture()) + " FCFA",
                        FormatUtil.montant(c.getSoldePrecedent()) + " FCFA",
                        FormatUtil.montant(c.getTotalSolde()) + " FCFA",
                        FormatUtil.montant(c.getMontantVerse()) + " FCFA",
                        FormatUtil.montant(c.getReste()) + " FCFA",
                        c.getStatut()
                    ));
                    totAtt = totAtt.add(c.getTotalSolde());
                    totRecu = totRecu.add(c.getMontantVerse());
                    totReste = totReste.add(c.getReste());
                }
                lblApercuResume.setText("Total Solde : " + FormatUtil.montant(totAtt) + " FCFA | Encaissé : " + FormatUtil.montant(totRecu) + " FCFA | Reste impayé : " + FormatUtil.montant(totReste) + " FCFA");
            }
            case "SOLDES" -> {
                List<Client> clients = (List<Client>) dataObj;
                ajouterColonnesApercu("Code", "Nom Client / Livreur", "Téléphone", "Adresse", "Solde Actuel");
                BigDecimal totSoldes = BigDecimal.ZERO;
                for (Client cl : clients) {
                    dataApercu.add(FXCollections.observableArrayList(
                        cl.getCode(),
                        cl.getNom(),
                        cl.getTelephone() != null ? cl.getTelephone() : "—",
                        cl.getAdresse() != null ? cl.getAdresse() : "—",
                        FormatUtil.montant(cl.getSoldeActuel()) + " FCFA"
                    ));
                    if (cl.getSoldeActuel() != null) totSoldes = totSoldes.add(cl.getSoldeActuel());
                }
                lblApercuResume.setText("Total des créances clients : " + FormatUtil.montant(totSoldes) + " FCFA (" + clients.size() + " comptes)");
            }
            case "VERSEMENTS" -> {
                List<Versement> versements = (List<Versement>) dataObj;
                ajouterColonnesApercu("N° Reçu", "Client / Livreur", "Attendu", "Remis", "Écart", "Mode");
                BigDecimal tot = BigDecimal.ZERO;
                for (Versement v : versements) {
                    dataApercu.add(FXCollections.observableArrayList(
                        v.getNumero(),
                        v.getClient() != null ? v.getClient().getNom() : "—",
                        FormatUtil.montant(v.getMontantAttendu()) + " FCFA",
                        FormatUtil.montant(v.getMontantRemis()) + " FCFA",
                        FormatUtil.montant(v.getEcart()) + " FCFA",
                        v.getModePaiement()
                    ));
                    if (v.getMontantRemis() != null) tot = tot.add(v.getMontantRemis());
                }
                lblApercuResume.setText("Versements du jour : " + FormatUtil.montant(tot) + " FCFA (" + versements.size() + " encaissements)");
            }
            default -> {
                List<JournalAudit> audits = (List<JournalAudit>) dataObj;
                ajouterColonnesApercu("Date / Heure", "Utilisateur", "Action", "Entité", "Détail");
                for (JournalAudit a : audits) {
                    dataApercu.add(FXCollections.observableArrayList(
                        a.getDateAction() != null ? a.getDateAction().toString().replace("T", " ") : "—",
                        a.getLoginUtilisateur(),
                        a.getAction(),
                        a.getEntite(),
                        a.getDetails()
                    ));
                }
                lblApercuResume.setText("Journal d'audit : " + audits.size() + " événements enregistrés");
            }
        }
        tableApercu.setItems(dataApercu);
    }

    private void ajouterColonnesApercu(String... titres) {
        tableApercu.getColumns().clear();
        for (int i = 0; i < titres.length; i++) {
            final int colIdx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(titres[i]);
            col.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue().size() > colIdx ? param.getValue().get(colIdx) : ""));
            col.setPrefWidth(titres.length > 5 ? 130 : 180);
            tableApercu.getColumns().add(col);
        }
    }

    private void exporterRapportCourantPDF() {
        String type = cboTypeRapport.getValue();
        LocalDate date = dpDateRapport.getValue() != null ? dpDateRapport.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter en PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF (*.pdf)", "*.pdf"));
        fc.setInitialFileName(type.replace(" ", "_").toLowerCase() + "_" + date + ".pdf");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            if ("Fiche Journalière des Sorties & Retours".equals(type)) {
                List<LigneCommande> sorties = ficheDAO.findLignesByDate(date);
                PdfService.exporterFicheSortie(date, sorties, f.getAbsolutePath());
            } else if ("Feuille de Facturation & Caisse Journalière".equals(type)) {
                List<FicheCaisseLigne> caisse = caisseService.chargerFicheCaisseJournaliere(date);
                PdfService.exporterFicheCaisse(date, caisse, f.getAbsolutePath());
            } else if ("Grand Livre des Soldes Clients".equals(type)) {
                List<Client> clients = clientDAO.findAll();
                PdfService.exporterSoldesClients(clients, f.getAbsolutePath());
            } else {
                List<Client> clients = clientDAO.findAll();
                PdfService.exporterSoldesClients(clients, f.getAbsolutePath());
            }
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Rapport PDF exporté : " + f.getName(), Alert.AlertType.INFORMATION));
    }

    private void exporterRapportCourantExcel() {
        String type = cboTypeRapport.getValue();
        LocalDate date = dpDateRapport.getValue() != null ? dpDateRapport.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter en Excel / CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Feuille Excel / CSV (*.csv)", "*.csv"));
        fc.setInitialFileName(type.replace(" ", "_").toLowerCase() + "_" + date + ".csv");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            if ("Fiche Journalière des Sorties & Retours".equals(type)) {
                List<LigneCommande> sorties = ficheDAO.findLignesByDate(date);
                ExcelExportService.exporterFicheSortieExcel(date, sorties, f);
            } else if ("Feuille de Facturation & Caisse Journalière".equals(type)) {
                List<FicheCaisseLigne> caisse = caisseService.chargerFicheCaisseJournaliere(date);
                ExcelExportService.exporterFicheCaisseExcel(date, caisse, f);
            } else {
                List<LigneCommande> sorties = ficheDAO.findLignesByDate(date);
                ExcelExportService.exporterFicheSortieExcel(date, sorties, f);
            }
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Rapport Excel exporté : " + f.getName(), Alert.AlertType.INFORMATION));
    }
}
