package com.boulangerie.ui.fx;

import com.boulangerie.dao.ClientDAO;
import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.model.Client;
import com.boulangerie.model.FicheJournaliere;
import com.boulangerie.model.LigneSortie;
import com.boulangerie.model.Produit;
import com.boulangerie.service.ExcelExportService;
import com.boulangerie.service.PdfService;
import com.boulangerie.service.SessionService;
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
 * Module Sorties — Enregistrement des produits sortis (pains, viennoiseries, pâtisseries...)
 * par client/livreur et génération des fiches de sortie conformes aux documents Excel.
 */
public class SortiesFxPanel extends FxPanelBase {

    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final SessionService session = SessionService.getInstance();

    private DatePicker dpDate;
    private TextField txtRecherche;
    private TableView<LigneSortie> table;
    private ObservableList<LigneSortie> data = FXCollections.observableArrayList();
    private List<LigneSortie> allLignes = List.of();

    private Label lblTotalSorties, lblTotalRetours, lblTotalNettes, lblMontantTotal;

    public SortiesFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        // Sélecteur de date
        dpDate = new DatePicker(LocalDate.now());
        dpDate.setPrefWidth(140);
        dpDate.setOnAction(e -> refresh());

        // Barre de recherche
        txtRecherche = new TextField();
        txtRecherche.setPromptText("Rechercher client, livreur, produit...");
        txtRecherche.setPrefWidth(240);
        txtRecherche.textProperty().addListener((obs, ov, nv) -> filtrer());

        // Boutons
        Button btnAjouter = btnPrimary("+ Enregistrer une sortie", BootstrapIcons.PLUS_CIRCLE);
        Button btnPDF = btnOutline("📄 Fiche de sortie (PDF)");
        Button btnExcel = btnOutline("📊 Exporter Excel");
        Button btnRefr = btnOutline("⟳ Actualiser");

        btnAjouter.setOnAction(e -> ouvrirFormulaireSortie(null));
        btnPDF.setOnAction(e -> exporterFichePDF());
        btnExcel.setOnAction(e -> exporterFicheExcel());
        btnRefr.setOnAction(e -> refresh());

        HBox toolBar = new HBox(10,
            new Label("Date :"), dpDate,
            txtRecherche,
            btnAjouter, btnPDF, btnExcel, btnRefr
        );
        toolBar.setAlignment(Pos.CENTER_LEFT);
        toolBar.setPadding(new Insets(0, 0, 8, 0));

        // Table
        table = styledTable();
        table.setItems(data);

        TableColumn<LigneSortie, String> colClient = new TableColumn<>("Client / Livreur");
        TableColumn<LigneSortie, String> colProduit = new TableColumn<>("Désignation Produit");
        TableColumn<LigneSortie, String> colQteSort = new TableColumn<>("Qté sortie");
        TableColumn<LigneSortie, String> colQteRet = new TableColumn<>("Qté retour");
        TableColumn<LigneSortie, String> colQteNet = new TableColumn<>("Qté nette");
        TableColumn<LigneSortie, String> colPrix = new TableColumn<>("Prix unitaire");
        TableColumn<LigneSortie, String> colTotal = new TableColumn<>("Total HT");
        TableColumn<LigneSortie, Void> colActions = new TableColumn<>("Actions");

        colClient.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClient() != null ? d.getValue().getClient().getNom() : "—"));
        colClient.setPrefWidth(180);

        colProduit.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getProduit() != null ? d.getValue().getProduit().getLibelle() : "—"));
        colProduit.setPrefWidth(200);

        colQteSort.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteSortie())));
        colQteSort.setStyle("-fx-alignment: CENTER-RIGHT;");
        colQteSort.setPrefWidth(90);

        colQteRet.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteRetournee())));
        colQteRet.setStyle("-fx-alignment: CENTER-RIGHT;");
        colQteRet.setPrefWidth(90);

        colQteNet.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteNette())));
        colQteNet.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        colQteNet.setPrefWidth(90);

        colPrix.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getTarifApplicable() != null ? FormatUtil.montant(d.getValue().getTarifApplicable()) : "0"));
        colPrix.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPrix.setPrefWidth(110);

        colTotal.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getMontantHt() != null ? FormatUtil.montant(d.getValue().getMontantHt()) : "0"));
        colTotal.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");
        colTotal.setPrefWidth(130);

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnModif = new Button("Modifier");
            private final Button btnSuppr = new Button("Suppr.");
            private final HBox box = new HBox(6, btnModif, btnSuppr);

            {
                btnModif.setStyle("-fx-font-size: 10px; -fx-padding: 3 8; -fx-background-color: #E8F0FB; -fx-text-fill: #2E5A88; -fx-cursor: hand;");
                btnSuppr.setStyle("-fx-font-size: 10px; -fx-padding: 3 8; -fx-background-color: #FDECEA; -fx-text-fill: #C62828; -fx-cursor: hand;");
                box.setAlignment(Pos.CENTER);

                btnModif.setOnAction(e -> {
                    LigneSortie l = getTableView().getItems().get(getIndex());
                    ouvrirFormulaireSortie(l);
                });
                btnSuppr.setOnAction(e -> {
                    LigneSortie l = getTableView().getItems().get(getIndex());
                    supprimerLigne(l);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        colActions.setPrefWidth(140);

        table.getColumns().addAll(colClient, colProduit, colQteSort, colQteRet, colQteNet, colPrix, colTotal, colActions);

        // Barre KPI en bas
        lblTotalSorties = createKpiBadge("Total Sorties : 0");
        lblTotalRetours = createKpiBadge("Total Retours : 0");
        lblTotalNettes = createKpiBadge("Total Net : 0");
        lblMontantTotal = createKpiBadge("Montant Total : 0 FCFA");
        lblMontantTotal.setStyle(lblMontantTotal.getStyle() + " -fx-text-fill: #1F3A5F;");

        HBox kpiBar = new HBox(12, lblTotalSorties, lblTotalRetours, lblTotalNettes, lblMontantTotal);
        kpiBar.setPadding(new Insets(8, 0, 0, 0));

        VBox body = new VBox(10,
            header("Enregistrement des Sorties & Fiches Journalières"),
            toolBar,
            table,
            kpiBar
        );
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    private Label createKpiBadge(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-color: white; "
            + "-fx-border-color: #D6CFC4; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 12;");
        return lbl;
    }

    @Override
    public void refresh() {
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        runAsync(() -> ficheDAO.findLignesByDate(date), lignes -> {
            this.allLignes = lignes;
            filtrer();
        });
    }

    private void filtrer() {
        String filter = txtRecherche.getText() != null ? txtRecherche.getText().trim().toLowerCase() : "";
        List<LigneSortie> filtered = allLignes.stream().filter(l -> {
            if (filter.isEmpty()) return true;
            String cl = l.getClient() != null ? l.getClient().getNom().toLowerCase() : "";
            String pr = l.getProduit() != null ? l.getProduit().getLibelle().toLowerCase() : "";
            return cl.contains(filter) || pr.contains(filter);
        }).toList();

        data.setAll(filtered);

        int totalSort = 0;
        int totalRet = 0;
        int totalNet = 0;
        BigDecimal montantTot = BigDecimal.ZERO;

        for (LigneSortie l : filtered) {
            totalSort += l.getQuantiteSortie();
            totalRet += l.getQuantiteRetournee();
            totalNet += l.getQuantiteNette();
            if (l.getMontantHt() != null) montantTot = montantTot.add(l.getMontantHt());
        }

        lblTotalSorties.setText("Total Sorties : " + totalSort + " pièces");
        lblTotalRetours.setText("Total Retours : " + totalRet + " pièces");
        lblTotalNettes.setText("Total Net : " + totalNet + " pièces");
        lblMontantTotal.setText("Montant Total : " + FormatUtil.montant(montantTot) + " FCFA");
    }

    private void ouvrirFormulaireSortie(LigneSortie existante) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle(existante == null ? "Enregistrer une sortie" : "Modifier la sortie");
        dlg.setHeaderText(null);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        List<Client> clients = clientDAO.findAll();
        List<Produit> produits = produitDAO.findAll(false);

        ComboBox<Client> cboClient = new ComboBox<>(FXCollections.observableArrayList(clients));
        cboClient.setPrefWidth(260);
        cboClient.setPromptText("Sélectionner le client / livreur...");

        ComboBox<Produit> cboProduit = new ComboBox<>(FXCollections.observableArrayList(produits));
        cboProduit.setPrefWidth(260);
        cboProduit.setPromptText("Sélectionner le produit...");

        TextField txtPrix = new TextField("0");
        txtPrix.setPrefWidth(120);

        Spinner<Integer> spnSortie = new Spinner<>(0, 99999, 0);
        spnSortie.setEditable(true);
        spnSortie.setPrefWidth(120);

        Spinner<Integer> spnRetour = new Spinner<>(0, 99999, 0);
        spnRetour.setEditable(true);
        spnRetour.setPrefWidth(120);

        TextField txtMotif = new TextField();
        txtMotif.setPromptText("Motif si retour (ex: invendu, abîmé)...");
        txtMotif.setPrefWidth(260);

        Label lblCalcul = new Label("Total calculé : 0 FCFA");
        lblCalcul.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        // Boutons rapides pour incrémenter les sorties
        HBox btnRapides = new HBox(6);
        for (int q : new int[]{10, 25, 50, 100}) {
            Button b = new Button("+" + q);
            b.setStyle("-fx-font-size: 11px; -fx-padding: 3 8; -fx-background-color: #EDE8DE; -fx-cursor: hand;");
            b.setOnAction(e -> spnSortie.getValueFactory().setValue(spnSortie.getValue() + q));
            btnRapides.getChildren().add(b);
        }

        // Écouteur pour recalcul immédiat
        Runnable recalculer = () -> {
            Produit p = cboProduit.getValue();
            if (p != null && (txtPrix.getText() == null || txtPrix.getText().isBlank() || txtPrix.getText().equals("0"))) {
                if (p.getPrixUnitaire() != null) {
                    txtPrix.setText(p.getPrixUnitaire().toPlainString());
                }
            }
            try {
                BigDecimal prix = new BigDecimal(txtPrix.getText().trim().replace(" ", "").replace(",", "."));
                int qteNette = spnSortie.getValue() - spnRetour.getValue();
                if (qteNette < 0) qteNette = 0;
                BigDecimal tot = prix.multiply(BigDecimal.valueOf(qteNette));
                lblCalcul.setText("Total calculé : " + FormatUtil.montant(tot) + " FCFA  (" + qteNette + " pièces nettes)");
            } catch (Exception ignored) {
                lblCalcul.setText("Total calculé : —");
            }
        };

        cboProduit.valueProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.getPrixUnitaire() != null) {
                txtPrix.setText(nv.getPrixUnitaire().toPlainString());
            }
            recalculer.run();
        });
        txtPrix.textProperty().addListener((obs, ov, nv) -> recalculer.run());
        spnSortie.valueProperty().addListener((obs, ov, nv) -> recalculer.run());
        spnRetour.valueProperty().addListener((obs, ov, nv) -> recalculer.run());

        if (existante != null) {
            if (existante.getClient() != null) {
                clients.stream().filter(c -> c.getId().equals(existante.getClient().getId())).findFirst().ifPresent(cboClient::setValue);
            }
            if (existante.getProduit() != null) {
                produits.stream().filter(p -> p.getId().equals(existante.getProduit().getId())).findFirst().ifPresent(cboProduit::setValue);
            }
            if (existante.getTarifApplicable() != null) txtPrix.setText(existante.getTarifApplicable().toPlainString());
            spnSortie.getValueFactory().setValue(existante.getQuantiteSortie());
            spnRetour.getValueFactory().setValue(existante.getQuantiteRetournee());
            if (existante.getMotifRetour() != null) txtMotif.setText(existante.getMotifRetour());
            recalculer.run();
        }

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        grid.addRow(0, new Label("Client / Livreur : *"), cboClient);
        grid.addRow(1, new Label("Produit : *"), cboProduit);
        grid.addRow(2, new Label("Prix unitaire (FCFA) :"), txtPrix);
        grid.addRow(3, new Label("Quantité sortie :"), new VBox(4, spnSortie, btnRapides));
        grid.addRow(4, new Label("Quantité retournée :"), spnRetour);
        grid.addRow(5, new Label("Motif retour :"), txtMotif);
        grid.addRow(6, new Label(""), lblCalcul);

        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(btn -> btn == ButtonType.OK);

        dlg.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            Client cl = cboClient.getValue();
            Produit pr = cboProduit.getValue();
            if (cl == null || pr == null) {
                mainWindow.showAlert("Validation", "Veuillez sélectionner un client et un produit.", Alert.AlertType.WARNING);
                return;
            }
            int qSort = spnSortie.getValue();
            int qRet = spnRetour.getValue();
            if (qRet > qSort) {
                mainWindow.showAlert("Validation", "La quantité retournée ne peut pas dépasser la quantité sortie.", Alert.AlertType.WARNING);
                return;
            }

            BigDecimal prix;
            try {
                prix = new BigDecimal(txtPrix.getText().trim().replace(" ", "").replace(",", "."));
            } catch (Exception ex) {
                prix = pr.getPrixUnitaire() != null ? pr.getPrixUnitaire() : BigDecimal.ZERO;
            }

            LocalDate dateFiche = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();

            runAsync(() -> {
                FicheJournaliere fj = ficheDAO.getOrCreateFicheJour(dateFiche, null, session.getUserId());
                if (existante == null) {
                    LigneSortie l = new LigneSortie();
                    l.setFicheId(fj.getId());
                    l.setClient(cl);
                    l.setProduit(pr);
                    l.setQuantiteSortie(qSort);
                    l.setQuantiteRetournee(qRet);
                    l.setTarifApplicable(prix);
                    l.setTypeTarif("Standard");
                    l.setMotifRetour(txtMotif.getText().trim());
                    l.setRemisePct(BigDecimal.ZERO);
                    ficheDAO.saveLigne(l);
                } else {
                    existante.setClient(cl);
                    existante.setProduit(pr);
                    existante.setQuantiteSortie(qSort);
                    existante.setQuantiteRetournee(qRet);
                    existante.setTarifApplicable(prix);
                    existante.setMotifRetour(txtMotif.getText().trim());
                    ficheDAO.updateLigne(existante);
                }
                ficheDAO.recalculerTotauxFiche(fj.getId());
                return true;
            }, success -> {
                refresh();
            });
        });
    }

    private void supprimerLigne(LigneSortie ligne) {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Voulez-vous supprimer cette sortie pour " + (ligne.getClient() != null ? ligne.getClient().getNom() : "") + " ?");
        conf.setTitle("Confirmation");
        conf.setHeaderText(null);
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            runAsync(() -> {
                ficheDAO.deleteLigne(ligne.getId());
                if (ligne.getFicheId() != null) {
                    ficheDAO.recalculerTotauxFiche(ligne.getFicheId());
                }
                return true;
            }, ok -> refresh());
        });
    }

    private void exporterFichePDF() {
        if (allLignes.isEmpty()) {
            mainWindow.showAlert("Information", "Aucune sortie enregistrée pour cette date.", Alert.AlertType.INFORMATION);
            return;
        }
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter la Fiche de Sortie PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF (*.pdf)", "*.pdf"));
        fc.setInitialFileName("FICHE_SORTIE_" + date + ".pdf");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            PdfService.exporterFicheSortie(date, allLignes, f.getAbsolutePath());
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Fiche de sortie PDF exportée avec succès :\n" + f.getName(), Alert.AlertType.INFORMATION));
    }

    private void exporterFicheExcel() {
        if (allLignes.isEmpty()) {
            mainWindow.showAlert("Information", "Aucune sortie enregistrée pour cette date.", Alert.AlertType.INFORMATION);
            return;
        }
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter la Fiche de Sortie Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Feuille Excel / CSV (*.csv)", "*.csv"));
        fc.setInitialFileName("SORTIE_" + date + ".csv");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            ExcelExportService.exporterFicheSortieExcel(date, allLignes, f);
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Fiche Excel exportée avec succès :\n" + f.getName(), Alert.AlertType.INFORMATION));
    }
}
