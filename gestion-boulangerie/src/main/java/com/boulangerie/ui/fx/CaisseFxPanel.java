package com.boulangerie.ui.fx;

import com.boulangerie.model.Client;
import com.boulangerie.model.FicheCaisseLigne;
import com.boulangerie.model.Recu;
import com.boulangerie.model.Versement;
import com.boulangerie.service.CaisseService;
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
 * Module Caisse & Rapprochement Journalier — Interface principale de caisse.
 * Permet de visualiser pour chaque client/livreur les montants attendus (sorties),
 * les soldes antérieurs, de saisir les montants reçus et de déterminer immédiatement
 * les impayés, écarts et restes dus, conformément aux fiches de facturation Excel.
 */
public class CaisseFxPanel extends FxPanelBase {

    private final CaisseService caisseService = new CaisseService();
    private final SessionService session = SessionService.getInstance();

    private DatePicker dpDate;
    private TextField txtRecherche;
    private TableView<FicheCaisseLigne> table;
    private ObservableList<FicheCaisseLigne> data = FXCollections.observableArrayList();
    private List<FicheCaisseLigne> allLignes = List.of();

    private Label lblFactureJour, lblSoldePrec, lblTotalAttendu, lblTotalRecu, lblResteImpaye;

    public CaisseFxPanel(MainWindow mainWindow) {
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
        txtRecherche.setPromptText("Rechercher un client ou livreur...");
        txtRecherche.setPrefWidth(240);
        txtRecherche.textProperty().addListener((obs, ov, nv) -> filtrer());

        // Boutons
        Button btnPDF = btnOutline("📄 Feuille de Caisse (PDF)");
        Button btnExcel = btnOutline("📊 Exporter Excel");
        Button btnClot = btnDanger("🔒 Clôturer la caisse", BootstrapIcons.LOCK_FILL);
        Button btnRefr = btnOutline("⟳ Actualiser");

        btnPDF.setOnAction(e -> exporterPDF());
        btnExcel.setOnAction(e -> exporterExcel());
        btnClot.setOnAction(e -> cloturerCaisse());
        btnRefr.setOnAction(e -> refresh());

        HBox toolBar = new HBox(10,
            new Label("Date :"), dpDate,
            txtRecherche,
            btnPDF, btnExcel, btnClot, btnRefr
        );
        toolBar.setAlignment(Pos.CENTER_LEFT);
        toolBar.setPadding(new Insets(0, 0, 8, 0));

        // Bannière KPI
        lblFactureJour = kpiCard("Factures du jour", "0 FCFA", "#1F3A5F");
        lblSoldePrec   = kpiCard("Soldes antérieurs", "0 FCFA", "#5F6368");
        lblTotalAttendu= kpiCard("Total à recouvrer", "0 FCFA", "#2E5A88");
        lblTotalRecu   = kpiCard("Total encaissé", "0 FCFA", "#2E7D32");
        lblResteImpaye = kpiCard("Reste impayé / Écarts", "0 FCFA", "#C62828");

        HBox kpiBanner = new HBox(12, lblFactureJour, lblSoldePrec, lblTotalAttendu, lblTotalRecu, lblResteImpaye);
        kpiBanner.setPadding(new Insets(0, 0, 10, 0));

        // Table Caisse
        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                FicheCaisseLigne sel = table.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getClient() != null) {
                    ouvrirDetailClient(sel.getClient());
                }
            }
        });

        TableColumn<FicheCaisseLigne, String> colClient = new TableColumn<>("Nom Livreur / Client");
        TableColumn<FicheCaisseLigne, String> colSorties = new TableColumn<>("Sorties (Pains & Produits)");
        TableColumn<FicheCaisseLigne, String> colFacture = new TableColumn<>("Facture Jour");
        TableColumn<FicheCaisseLigne, String> colEcartPrec = new TableColumn<>("Écart Précédent");
        TableColumn<FicheCaisseLigne, String> colTotalSolde = new TableColumn<>("Total Solde");
        TableColumn<FicheCaisseLigne, String> colVersement = new TableColumn<>("Montant Reçu");
        TableColumn<FicheCaisseLigne, String> colReste = new TableColumn<>("Reste / Écart");
        TableColumn<FicheCaisseLigne, String> colStatut = new TableColumn<>("Statut");
        TableColumn<FicheCaisseLigne, Void> colActions = new TableColumn<>("Actions");

        colClient.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClient() != null ? d.getValue().getClient().getNom() : "—"));
        colClient.setPrefWidth(180);

        colSorties.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getResumeSorties().isEmpty() ? "Aucune sortie" : d.getValue().getResumeSorties()));
        colSorties.setPrefWidth(220);

        colFacture.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantFacture())));
        colFacture.setStyle("-fx-alignment: CENTER-RIGHT;");
        colFacture.setPrefWidth(110);

        colEcartPrec.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getSoldePrecedent())));
        colEcartPrec.setStyle("-fx-alignment: CENTER-RIGHT;");
        colEcartPrec.setPrefWidth(110);

        colTotalSolde.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTotalSolde())));
        colTotalSolde.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");
        colTotalSolde.setPrefWidth(120);

        colVersement.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantVerse())));
        colVersement.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        colVersement.setPrefWidth(120);

        colReste.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getReste())));
        colReste.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    FicheCaisseLigne l = getTableView().getItems().get(getIndex());
                    if (l.getReste().compareTo(BigDecimal.ZERO) > 0) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #C62828;");
                    } else if (l.getReste().compareTo(BigDecimal.ZERO) < 0) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E5A88;");
                    } else {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
                    }
                }
            }
        });
        colReste.setPrefWidth(120);

        colStatut.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatut()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(creerBadgeStatut(item));
                }
            }
        });
        colStatut.setPrefWidth(110);

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button("📋 Dates Sorties");
            private final Button btnEncaisser = new Button("💵 Encaisser");
            private final Button btnRecu = new Button("🧾");
            private final HBox box = new HBox(4, btnDetail, btnEncaisser, btnRecu);

            {
                btnDetail.setStyle("-fx-font-size: 11px; -fx-padding: 3 6; -fx-background-color: #E8F0FE; -fx-text-fill: #1A73E8; -fx-font-weight: bold; -fx-cursor: hand;");
                btnEncaisser.setStyle("-fx-font-size: 11px; -fx-padding: 3 6; -fx-background-color: #EDF7EE; -fx-text-fill: #2E7D32; -fx-font-weight: bold; -fx-cursor: hand;");
                btnRecu.setStyle("-fx-font-size: 11px; -fx-padding: 3 6; -fx-background-color: #F4F0E8; -fx-text-fill: #1A2733; -fx-cursor: hand;");
                box.setAlignment(Pos.CENTER);

                btnDetail.setOnAction(e -> {
                    FicheCaisseLigne l = getTableView().getItems().get(getIndex());
                    if (l != null && l.getClient() != null) ouvrirDetailClient(l.getClient());
                });
                btnEncaisser.setOnAction(e -> {
                    FicheCaisseLigne l = getTableView().getItems().get(getIndex());
                    if (l != null) ouvrirDialogueEncaissement(l);
                });
                btnRecu.setOnAction(e -> {
                    FicheCaisseLigne l = getTableView().getItems().get(getIndex());
                    if (l != null) afficherRecu(l);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        colActions.setPrefWidth(220);

        table.getColumns().addAll(colClient, colSorties, colFacture, colEcartPrec, colTotalSolde, colVersement, colReste, colStatut, colActions);

        VBox body = new VBox(10,
            header("Caisse & Rapprochement Journalier"),
            kpiBanner,
            toolBar,
            table
        );
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    private Label kpiCard(String titre, String valeur, String couleur) {
        Label lbl = new Label(titre + "\n" + valeur);
        lbl.setStyle("-fx-background-color: white; -fx-border-color: #D6CFC4; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 10 16; -fx-font-size: 11px; -fx-text-fill: #5F6368; "
            + "-fx-line-spacing: 4px; -fx-alignment: CENTER-LEFT;");
        lbl.setPrefWidth(210);
        return lbl;
    }

    private Label creerBadgeStatut(String statut) {
        Label b = new Label(statut);
        b.setStyle("-fx-padding: 3 8; -fx-background-radius: 12; -fx-font-size: 10px; -fx-font-weight: bold;");
        switch (statut) {
            case "Soldé"   -> b.setStyle(b.getStyle() + " -fx-background-color: #EDF7EE; -fx-text-fill: #2E7D32;");
            case "Partiel" -> b.setStyle(b.getStyle() + " -fx-background-color: #FFF3E0; -fx-text-fill: #D4890A;");
            case "Excédent"-> b.setStyle(b.getStyle() + " -fx-background-color: #E8F0FB; -fx-text-fill: #2E5A88;");
            case "Non versé"->b.setStyle(b.getStyle() + " -fx-background-color: #FDECEA; -fx-text-fill: #C62828;");
            default        -> b.setStyle(b.getStyle() + " -fx-background-color: #F4F0E8; -fx-text-fill: #6B7A8D;");
        }
        return b;
    }

    @Override
    public void refresh() {
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        runAsync(() -> caisseService.chargerFicheCaisseJournaliere(date), lignes -> {
            this.allLignes = lignes;
            filtrer();
        });
    }

    private void filtrer() {
        String query = txtRecherche.getText() != null ? txtRecherche.getText().trim().toLowerCase() : "";
        List<FicheCaisseLigne> filtered = allLignes.stream().filter(l -> {
            if (query.isEmpty()) return true;
            String nom = l.getClient() != null ? l.getClient().getNom().toLowerCase() : "";
            return nom.contains(query);
        }).toList();

        data.setAll(filtered);

        BigDecimal totFac = BigDecimal.ZERO;
        BigDecimal totSoldePrec = BigDecimal.ZERO;
        BigDecimal totAttendu = BigDecimal.ZERO;
        BigDecimal totRecu = BigDecimal.ZERO;
        BigDecimal totReste = BigDecimal.ZERO;

        for (FicheCaisseLigne fl : filtered) {
            totFac = totFac.add(fl.getMontantFacture());
            totSoldePrec = totSoldePrec.add(fl.getSoldePrecedent());
            totAttendu = totAttendu.add(fl.getTotalSolde());
            totRecu = totRecu.add(fl.getMontantVerse());
            totReste = totReste.add(fl.getReste());
        }

        lblFactureJour.setText("Factures du jour\n" + FormatUtil.montant(totFac) + " FCFA");
        lblSoldePrec.setText("Soldes antérieurs\n" + FormatUtil.montant(totSoldePrec) + " FCFA");
        lblTotalAttendu.setText("Total à recouvrer\n" + FormatUtil.montant(totAttendu) + " FCFA");
        lblTotalRecu.setText("Total encaissé\n" + FormatUtil.montant(totRecu) + " FCFA");
        lblResteImpaye.setText("Reste impayé / Écarts\n" + FormatUtil.montant(totReste) + " FCFA");
    }

    private void ouvrirDialogueEncaissement(FicheCaisseLigne ligne) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Encaissement — " + (ligne.getClient() != null ? ligne.getClient().getNom() : ""));
        dlg.setHeaderText(null);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label lblClient = new Label(ligne.getClient() != null ? ligne.getClient().getNom() : "—");
        lblClient.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label lblSorties = new Label(ligne.getResumeSorties().isEmpty() ? "Aucune sortie ce jour" : ligne.getResumeSorties());
        lblSorties.setStyle("-fx-font-size: 11px; -fx-text-fill: #5F6368; -fx-wrap-text: true;");

        Label lblTotalDu = new Label(FormatUtil.montant(ligne.getTotalSolde()) + " FCFA");
        lblTotalDu.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        // Champ montant reçu
        BigDecimal suggestMontant = ligne.getTotalSolde().compareTo(BigDecimal.ZERO) > 0 ? ligne.getTotalSolde() : BigDecimal.ZERO;
        if (ligne.getMontantVerse().compareTo(BigDecimal.ZERO) > 0) {
            suggestMontant = ligne.getMontantVerse();
        }
        TextField txtMontant = new TextField(suggestMontant.toPlainString());
        txtMontant.setPrefWidth(180);
        txtMontant.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        ComboBox<String> cboMode = new ComboBox<>(FXCollections.observableArrayList("Espèces", "Mobile Money (Wave/Orange/MTN)", "Chèque", "Virement"));
        cboMode.setValue("Espèces");
        cboMode.setPrefWidth(260);

        Label lblDiff = new Label();
        lblDiff.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        TextField txtMotif = new TextField();
        txtMotif.setPromptText("Motif si écart ou paiement partiel...");
        txtMotif.setPrefWidth(260);

        // Bouton règlement intégral en un clic
        Button btnTotal = new Button("Règlement intégral (" + FormatUtil.montant(ligne.getTotalSolde()) + ")");
        btnTotal.setStyle("-fx-font-size: 11px; -fx-background-color: #E8F0FE; -fx-text-fill: #1A73E8; -fx-cursor: hand;");
        btnTotal.setOnAction(e -> txtMontant.setText(ligne.getTotalSolde().toPlainString()));

        Runnable calculerEcart = () -> {
            try {
                BigDecimal recu = new BigDecimal(txtMontant.getText().trim().replace(" ", "").replace(",", "."));
                BigDecimal ecart = recu.subtract(ligne.getTotalSolde());
                if (ecart.compareTo(BigDecimal.ZERO) == 0) {
                    lblDiff.setText("Compte soldé : 0 FCFA");
                    lblDiff.setStyle("-fx-text-fill: #137333; -fx-font-weight: bold;");
                } else if (ecart.compareTo(BigDecimal.ZERO) < 0) {
                    lblDiff.setText("⚠️ Reste à recouvrer : " + FormatUtil.montant(ecart.abs()) + " FCFA");
                    lblDiff.setStyle("-fx-text-fill: #D93025; -fx-font-weight: bold;");
                    if (txtMotif.getText().isBlank()) {
                        txtMotif.setText("Paiement partiel — Reste " + FormatUtil.montant(ecart.abs()) + " FCFA");
                    }
                } else {
                    lblDiff.setText("ℹ️ Trop-perçu / Avance : +" + FormatUtil.montant(ecart) + " FCFA");
                    lblDiff.setStyle("-fx-text-fill: #1A73E8; -fx-font-weight: bold;");
                }
            } catch (Exception ex) {
                lblDiff.setText("—");
            }
        };

        txtMontant.textProperty().addListener((obs, ov, nv) -> calculerEcart.run());
        calculerEcart.run();

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        grid.addRow(0, new Label("Client / Livreur :"), lblClient);
        grid.addRow(1, new Label("Sorties du jour :"), lblSorties);
        grid.addRow(2, new Label("Facture jour :"), new Label(FormatUtil.montant(ligne.getMontantFacture()) + " FCFA"));
        grid.addRow(3, new Label("Solde antérieur :"), new Label(FormatUtil.montant(ligne.getSoldePrecedent()) + " FCFA"));
        grid.addRow(4, new Label("TOTAL SOLDE À PAYER :"), lblTotalDu);
        grid.addRow(5, new Label("Montant reçu : *"), new VBox(4, txtMontant, btnTotal));
        grid.addRow(6, new Label("Mode de paiement :"), cboMode);
        grid.addRow(7, new Label("Résultat :"), lblDiff);
        grid.addRow(8, new Label("Observations / Motif :"), txtMotif);

        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(btn -> btn == ButtonType.OK);

        dlg.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            BigDecimal montant;
            try {
                montant = new BigDecimal(txtMontant.getText().trim().replace(" ", "").replace(",", "."));
            } catch (Exception ex) {
                mainWindow.showAlert("Erreur", "Montant saisi invalide.", Alert.AlertType.ERROR);
                return;
            }

            runAsync(() -> {
                caisseService.encaisserLigneCaisse(ligne, montant, cboMode.getValue(), txtMotif.getText().trim());
                return true;
            }, success -> {
                refresh();
                mainWindow.showAlert("Succès", "Versement enregistré avec succès pour " + ligne.getClient().getNom() + ".\n"
                    + "Montant reçu : " + FormatUtil.montant(montant) + " FCFA", Alert.AlertType.INFORMATION);
            });
        });
    }

    private void afficherRecu(FicheCaisseLigne ligne) {
        if (ligne.getVersement() == null) {
            mainWindow.showAlert("Reçu", "Aucun versement enregistré ce jour pour " + (ligne.getClient() != null ? ligne.getClient().getNom() : ""), Alert.AlertType.INFORMATION);
            return;
        }
        Versement v = ligne.getVersement();
        Alert al = new Alert(Alert.AlertType.INFORMATION);
        al.setTitle("Reçu de Versement — " + v.getNumero());
        al.setHeaderText("Reçu N° " + v.getNumero());
        al.setContentText("Date : " + FormatUtil.date(v.getDateVersement()) + "\n"
            + "Client : " + (ligne.getClient() != null ? ligne.getClient().getNom() : "") + "\n"
            + "Montant Attendu : " + FormatUtil.montant(v.getMontantAttendu()) + " FCFA\n"
            + "Montant Reçu : " + FormatUtil.montant(v.getMontantRemis()) + " FCFA\n"
            + "Écart / Reste : " + FormatUtil.montant(v.getEcart()) + " FCFA\n"
            + "Mode : " + v.getModePaiement() + "\n"
            + (v.getMotifEcart() != null && !v.getMotifEcart().isBlank() ? "Motif : " + v.getMotifEcart() : ""));
        al.showAndWait();
    }

    private void cloturerCaisse() {
        if (!session.hasPermission("CLOTURE_WRITE") && !session.isAdmin()) {
            mainWindow.showAlert("Accès restreint", "Vous n'avez pas l'autorisation de clôturer la caisse.", Alert.AlertType.WARNING);
            return;
        }
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Confirmez-vous la clôture définitive de la caisse pour la date du " + FormatUtil.date(date) + " ?");
        conf.setTitle("Clôture de Caisse");
        conf.setHeaderText(null);
        conf.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            runAsync(() -> {
                caisseService.cloturerJour(date, null);
                return true;
            }, ok -> {
                mainWindow.showAlert("Succès", "Clôture de la journée " + FormatUtil.date(date) + " validée avec succès.", Alert.AlertType.INFORMATION);
                refresh();
            });
        });
    }

    private void exporterPDF() {
        if (allLignes.isEmpty()) {
            mainWindow.showAlert("Information", "Aucune donnée de caisse pour cette date.", Alert.AlertType.INFORMATION);
            return;
        }
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter la Feuille de Caisse PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF (*.pdf)", "*.pdf"));
        fc.setInitialFileName("FEUILLE_CAISSE_" + date + ".pdf");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            PdfService.exporterFicheCaisse(date, allLignes, f.getAbsolutePath());
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Feuille de Caisse exportée avec succès :\n" + f.getName(), Alert.AlertType.INFORMATION));
    }

    private void exporterExcel() {
        if (allLignes.isEmpty()) {
            mainWindow.showAlert("Information", "Aucune donnée de caisse pour cette date.", Alert.AlertType.INFORMATION);
            return;
        }
        LocalDate date = dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter la Feuille de Caisse Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Feuille Excel / CSV (*.csv)", "*.csv"));
        fc.setInitialFileName("CAISSE_FACTURATION_" + date + ".csv");
        File f = fc.showSaveDialog(mainWindow.getStage());
        if (f == null) return;

        runAsync(() -> {
            ExcelExportService.exporterFicheCaisseExcel(date, allLignes, f);
            return true;
        }, ok -> mainWindow.showAlert("Succès", "Feuille Excel exportée avec succès :\n" + f.getName(), Alert.AlertType.INFORMATION));
    }

    private void ouvrirDetailClient(Client client) {
        ClientDetailCaisseDialog dlg = new ClientDetailCaisseDialog(client);
        dlg.showAndWait();
        refresh();
    }
}
