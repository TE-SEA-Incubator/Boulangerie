package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.FacturationService;
import com.boulangerie.service.SessionService;
import com.boulangerie.service.TarifService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.time.LocalDate;
import java.util.List;

public class SortiesFxPanel extends FxPanelBase {

    private final FicheJournaliereDAO ficheDAO   = new FicheJournaliereDAO();
    private final FacturationService  factService = new FacturationService();
    private final SessionService      session    = SessionService.getInstance();

    private TableView<FicheJournaliere>       table;
    private ObservableList<FicheJournaliere>  data = FXCollections.observableArrayList();
    private Label                             lblTotaux;

    public SortiesFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        Button btnNouv = btnPrimary("+ Nouvelle fiche", BootstrapIcons.PLUS_CIRCLE);
        Button btnClot  = btnDanger("Clôturer + Facturer", BootstrapIcons.CHECK2_CIRCLE);
        Button btnRefr  = btnOutline("⟳ Actualiser");

        btnNouv.setOnAction(e  -> nouvelleFiche());
        btnClot.setOnAction(e  -> cloturerSelection());
        btnRefr.setOnAction(e  -> refresh());

        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if(e.getClickCount()==2) ouvrirSaisie(table.getSelectionModel().getSelectedItem()); });

        TableColumn<FicheJournaliere,String> colNum  = new TableColumn<>("N° Fiche");
        TableColumn<FicheJournaliere,String> colDate = new TableColumn<>("Date");
        TableColumn<FicheJournaliere,String> colLiv  = new TableColumn<>("Livreur");
        TableColumn<FicheJournaliere,String> colStat = new TableColumn<>("État");
        TableColumn<FicheJournaliere,String> colLig  = new TableColumn<>("Nb lignes");
        TableColumn<FicheJournaliere,String> colSort = new TableColumn<>("Total sorties");
        TableColumn<FicheJournaliere,String> colRet  = new TableColumn<>("Total retours");
        TableColumn<FicheJournaliere,String> colNet  = new TableColumn<>("Total net");

        colNum .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateFiche())));
        colLiv .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getLivreur() != null ? d.getValue().getLivreur().getNomComplet() : "—"));
        colStat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatut().name()));
        colStat.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });
        colLig .setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getNbLignes())));
        colSort.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTotalSorties())));
        colRet .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTotalRetours())));
        colNet .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTotalNet())));
        colNet.setStyle("-fx-font-weight: bold;");

        table.getColumns().addAll(colNum, colDate, colLiv, colStat, colLig, colSort, colRet, colNet);

        lblTotaux = footerCount("0 fiche(s)");

        VBox body = new VBox(10,
            header("Fiches journalières — Sorties & Retours", btnNouv, btnClot, btnRefr),
            table, lblTotaux);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        runAsync(() -> ficheDAO.findByDate(LocalDate.now()), list -> {
            data.setAll(list);
            // Totaux
            var totalNet = list.stream().map(FicheJournaliere::getTotalNet)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            lblTotaux.setText(list.size() + " fiche(s) aujourd'hui  |  "
                + "Total net : " + FormatUtil.montant(totalNet) + " FCFA");
        });
    }

    private void nouvelleFiche() {
        // Choisir livreur
        runAsync(() -> new UtilisateurDAO().findLivreurs(), livreurs -> {
            if (livreurs.isEmpty()) {
                mainWindow.showAlert("Info", "Aucun livreur disponible.", Alert.AlertType.INFORMATION); return;
            }
            ChoiceDialog<Utilisateur> dlg = new ChoiceDialog<>(livreurs.get(0), livreurs);
            dlg.setTitle("Nouvelle fiche"); dlg.setHeaderText(null);
            dlg.setContentText("Sélectionner le livreur :");
            dlg.showAndWait().ifPresent(livreur -> runAsync(() -> {
                FicheJournaliere f = new FicheJournaliere();
                f.setDateFiche(LocalDate.now());
                f.setLivreur(livreur);
                f.setNumero(ficheDAO.genererNumero(LocalDate.now()));
                f.setStatut(FicheJournaliere.Statut.EnCours);
                f.setCreePar(session.getUserId());
                String id = ficheDAO.save(f);
                f.setId(id);
                return ficheDAO.findById(id).orElse(f);
            }, fiche -> {
                refresh();
                ouvrirSaisie(fiche);
            }));
        });
    }

    private void ouvrirSaisie(FicheJournaliere f) {
        if (f == null) return;
        // Dialog de saisie des lignes
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Saisie — " + f.getNumero());
        dlg.setHeaderText("Livreur : " + (f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—")
            + "   Date : " + FormatUtil.date(f.getDateFiche()));
        dlg.getDialogPane().setPrefSize(900, 620);
        dlg.getDialogPane().setContent(buildSaisieContent(f));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dlg.showAndWait();
        refresh();
    }

    private VBox buildSaisieContent(FicheJournaliere fiche) {
        // Table des lignes existantes
        TableView<LigneSortie> tblLignes = new TableView<>(
            FXCollections.observableArrayList(fiche.getLignes()));
        tblLignes.setPrefHeight(200);

        TableColumn<LigneSortie,String> c1 = new TableColumn<>("Client");
        c1.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getClient()!=null?d.getValue().getClient().getNom():"—"));
        TableColumn<LigneSortie,String> c2 = new TableColumn<>("Produit");
        c2.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProduit()!=null?d.getValue().getProduit().getLibelle():"—"));
        TableColumn<LigneSortie,String> c3 = new TableColumn<>("Qté sortie");
        c3.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteSortie())));
        TableColumn<LigneSortie,String> c4 = new TableColumn<>("Qté retournée");
        c4.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteRetournee())));
        TableColumn<LigneSortie,String> c5 = new TableColumn<>("Tarif");
        c5.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTarifApplicable())));
        TableColumn<LigneSortie,String> c6 = new TableColumn<>("Montant HT");
        c6.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantHt())));
        tblLignes.getColumns().addAll(c1,c2,c3,c4,c5,c6);

        // Formulaire ajout ligne
        List<Client>  clients  = new ClientDAO().findAll();
        List<Produit> produits = new ProduitDAO().findAll(false);
        ComboBox<Client>  cboClient  = new ComboBox<>(FXCollections.observableArrayList(clients));
        ComboBox<Produit> cboProduit = new ComboBox<>(FXCollections.observableArrayList(produits));
        Spinner<Integer> spnSort = new Spinner<>(0, 9999, 0);
        Spinner<Integer> spnRet  = new Spinner<>(0, 9999, 0);
        ComboBox<String> cboMotif = new ComboBox<>(FXCollections.observableArrayList(
            "", "Produit abîmé", "Invendu", "Erreur quantité", "Autre"));

        Label lblTarif = new Label("Tarif : —");
        TarifService tarifSvc = new TarifService();

        // Recalcul tarif au changement
        javafx.beans.value.ChangeListener<Object> recalc = (o,ov,nv) -> {
            Client cl  = cboClient.getValue();
            Produit pr = cboProduit.getValue();
            if (cl != null && pr != null && pr.getId() != null) {
                try {
                    var tr = tarifSvc.resoudre(pr.getId(), cl, spnSort.getValue(), fiche.getDateFiche());
                    lblTarif.setText("Tarif : " + FormatUtil.montant(tr.prix())
                        + " FCFA  (" + tr.typeTarif() + ")");
                } catch (Exception e) { lblTarif.setText("Tarif : —"); }
            }
        };
        cboClient.valueProperty().addListener(recalc);
        cboProduit.valueProperty().addListener(recalc);
        spnSort.valueProperty().addListener(recalc);

        Button btnAdd = new Button("+ Ajouter la ligne");
        btnAdd.setStyle("-fx-background-color:#1A73E8; -fx-text-fill:white; -fx-cursor:hand;");
        btnAdd.setOnAction(e -> {
            Client cl  = cboClient.getValue();
            Produit pr = cboProduit.getValue();
            if (cl == null || pr == null) return;
            int qSort = spnSort.getValue(), qRet = spnRet.getValue();
            if (qRet > qSort) { mainWindow.showAlert("Validation","Qté retournée > sortie.",Alert.AlertType.WARNING); return; }
            if (qRet > 0 && (cboMotif.getValue() == null || cboMotif.getValue().isBlank())) {
                mainWindow.showAlert("Validation","Motif retour obligatoire.",Alert.AlertType.WARNING); return; }
            if (cl.isBloque()) { mainWindow.showAlert("Blocage","Client bloqué — sortie refusée.",Alert.AlertType.ERROR); return; }

            TarifService.TarifResolu tr;
            try { tr = tarifSvc.resoudre(pr.getId(), cl, qSort, fiche.getDateFiche()); }
            catch (Exception ex) { tr = new TarifService.TarifResolu(java.math.BigDecimal.ZERO,"Standard",java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null); }

            LigneSortie l = new LigneSortie();
            l.setFicheId(fiche.getId());
            l.setClient(cl); l.setProduit(pr);
            l.setQuantiteSortie(qSort); l.setQuantiteRetournee(qRet);
            l.setTarifApplicable(tr.prix()); l.setTypeTarif(tr.typeTarif());
            l.setRemisePct(tr.remisePct());
            if (qRet > 0) l.setMotifRetour(cboMotif.getValue());

            ficheDAO.saveLigne(l);
            fiche.getLignes().add(l);
            fiche.recalculerTotaux();
            ficheDAO.updateTotaux(fiche.getId(), fiche.getTotalSorties(), fiche.getTotalRetours(), fiche.getTotalNet());
            tblLignes.getItems().add(l);
            spnSort.getValueFactory().setValue(0); spnRet.getValueFactory().setValue(0);
        });

        GridPane addForm = new GridPane();
        addForm.setHgap(8); addForm.setVgap(6); addForm.setPadding(new Insets(8,0,8,0));
        addForm.addRow(0, new Label("Client"), cboClient, new Label("Produit"), cboProduit);
        addForm.addRow(1, new Label("Qté sortie"), spnSort, new Label("Qté retournée"), spnRet);
        addForm.addRow(2, lblTarif, new Label(), new Label("Motif retour"), cboMotif);
        addForm.addRow(3, btnAdd);

        VBox box = new VBox(10, new Label("Lignes de sortie :"), tblLignes,
            new Separator(), new Label("Ajouter une ligne :"), addForm);
        box.setPadding(new Insets(8));
        return box;
    }

    private void cloturerSelection() {
        FicheJournaliere sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { mainWindow.showAlert("Info","Sélectionnez une fiche.",Alert.AlertType.INFORMATION); return; }
        if (sel.getStatut() == FicheJournaliere.Statut.Clôturée) {
            mainWindow.showAlert("Information", "Cette fiche est déjà clôturée et facturée.", Alert.AlertType.INFORMATION);
            return;
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Clôturer " + sel.getNumero() + " et générer les factures ?");
        conf.setTitle("Confirmation"); conf.setHeaderText(null);
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            runAsync(() -> {
                FicheJournaliere fiche = ficheDAO.findById(sel.getId())
                    .orElseThrow(() -> new IllegalStateException("Fiche introuvable."));
                factService.genererDepuisFiche(fiche);
                ficheDAO.updateStatut(fiche.getId(), FicheJournaliere.Statut.Clôturée);
                return true;
            }, ok -> {
                refresh();
                mainWindow.showAlert("Succès","Fiche clôturée et factures générées.",Alert.AlertType.INFORMATION);
            });
        });
    }
}
