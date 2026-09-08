package com.boulangerie.ui.fx;

import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.dao.VersementDAO;
import com.boulangerie.model.Client;
import com.boulangerie.model.LigneCommande;
import com.boulangerie.model.Versement;
import com.boulangerie.service.CaisseService;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fenêtre détaillée pour un client sélectionné dans la caisse.
 * Affiche l'historique des dates de sortie, les commandes de chaque date
 * ainsi que les règlements et l'accumulation/réduction des dettes.
 */
public class ClientDetailCaisseDialog extends Dialog<Boolean> {

    private final Client client;
    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final VersementDAO versementDAO = new VersementDAO();
    private final CaisseService caisseService = new CaisseService();

    private ListView<LocalDate> lstDates;
    private TableView<LigneCommande> tableCommandes;
    private TableView<Versement> tableVersements;
    private ObservableList<LigneCommande> dataCommandes = FXCollections.observableArrayList();
    private ObservableList<Versement> dataVersements = FXCollections.observableArrayList();

    private Label lblTotalSortiesJour, lblTotalVersesJour, lblResteJour, lblSoldeGlobal;

    public ClientDetailCaisseDialog(Client client) {
        this.client = client;
        setTitle("Fiche Caisse & Sorties — " + client.getNom() + " (" + client.getCode() + ")");
        setHeaderText(null);
        getDialogPane().setPrefSize(920, 640);
        buildUI();
        chargerDates();
    }

    private void buildUI() {
        // En-tête Client
        VBox headerBox = new VBox(6);
        headerBox.setPadding(new Insets(12, 16, 12, 16));
        headerBox.setStyle("-fx-background-color: #F8F9FA; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 1 0;");

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label lblName = new Label(client.getNom() + " (" + client.getCode() + ")");
        lblName.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");

        Label lblCat = new Label(client.getCategorie() != null ? client.getCategorie().getNom() : "Client");
        lblCat.setStyle("-fx-background-color: #E8F0FE; -fx-text-fill: #1A73E8; -fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblSoldeGlobal = new Label("Dette accumulée : " + FormatUtil.montant(client.getSoldeActuel()) + " FCFA");
        lblSoldeGlobal.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
            + (client.getSoldeActuel().compareTo(BigDecimal.ZERO) > 0 ? "#C62828;" : "#2E7D32;"));

        titleRow.getChildren().addAll(lblName, lblCat, spacer, lblSoldeGlobal);

        Label lblSub = new Label("Adresse / Quartier : " + (client.getQuartier() != null ? client.getQuartier() : "—")
            + " | Téléphone : " + (client.getTelephone() != null ? client.getTelephone() : "—"));
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #5F6368;");

        headerBox.getChildren().addAll(titleRow, lblSub);

        // Liste des dates (gauche)
        lstDates = new ListView<>();
        lstDates.setPrefWidth(200);
        lstDates.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("📅 Sortie du " + FormatUtil.date(item));
                    setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                }
            }
        });
        lstDates.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) chargerDetailsDate(nv);
        });

        VBox leftBox = new VBox(6, new Label("Dates de Sortie :"), lstDates);
        leftBox.setPadding(new Insets(10));
        VBox.setVgrow(lstDates, Priority.ALWAYS);

        // Panneau Détails (droite)
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));

        Label lblCmdTitle = new Label("Commandes / Sorties de la date");
        lblCmdTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1F3A5F;");

        tableCommandes = new TableView<>();
        tableCommandes.setItems(dataCommandes);
        tableCommandes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableCommandes.setPrefHeight(180);

        TableColumn<LigneCommande, String> colProd = new TableColumn<>("Désignation Produit");
        colProd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProduit() != null ? d.getValue().getProduit().getLibelle() : "—"));

        TableColumn<LigneCommande, String> colSortie = new TableColumn<>("Sorties");
        colSortie.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteSortie())));
        colSortie.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<LigneCommande, String> colRetour = new TableColumn<>("Retours");
        colRetour.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteRetournee())));
        colRetour.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<LigneCommande, String> colNet = new TableColumn<>("Qté Nette");
        colNet.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantiteNette())));
        colNet.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        TableColumn<LigneCommande, String> colPrix = new TableColumn<>("Prix Unit.");
        colPrix.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getPrixUnitaire())));
        colPrix.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<LigneCommande, String> colMontant = new TableColumn<>("Montant HT");
        colMontant.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantHt())));
        colMontant.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");

        tableCommandes.getColumns().addAll(colProd, colSortie, colRetour, colNet, colPrix, colMontant);

        // Table Versements
        Label lblVersTitle = new Label("Versements & Règlements de la date");
        lblVersTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2E7D32;");

        tableVersements = new TableView<>();
        tableVersements.setItems(dataVersements);
        tableVersements.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableVersements.setPrefHeight(120);

        TableColumn<Versement, String> colVNum = new TableColumn<>("N° Reçu");
        colVNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));

        TableColumn<Versement, String> colVMode = new TableColumn<>("Mode");
        colVMode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getModePaiement() != null ? d.getValue().getModePaiement() : "Espèces"));

        TableColumn<Versement, String> colVRemis = new TableColumn<>("Montant Reçu");
        colVRemis.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantRemis())));
        colVRemis.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");

        TableColumn<Versement, String> colVMotif = new TableColumn<>("Observations / Motif");
        colVMotif.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMotifEcart() != null ? d.getValue().getMotifEcart() : "—"));

        tableVersements.getColumns().addAll(colVNum, colVMode, colVRemis, colVMotif);

        // Synthèse de la date
        HBox dateSummaryBox = new HBox(20);
        dateSummaryBox.setPadding(new Insets(8, 12, 8, 12));
        dateSummaryBox.setStyle("-fx-background-color: #F4F6FA; -fx-border-color: #DADCE0; -fx-border-radius: 6; -fx-background-radius: 6;");

        lblTotalSortiesJour = new Label("Commandes : 0 FCFA");
        lblTotalSortiesJour.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        lblTotalVersesJour = new Label("Versé : 0 FCFA");
        lblTotalVersesJour.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #2E7D32;");

        lblResteJour = new Label("Solde du jour : 0 FCFA");
        lblResteJour.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        dateSummaryBox.getChildren().addAll(lblTotalSortiesJour, lblTotalVersesJour, lblResteJour);

        rightBox.getChildren().addAll(lblCmdTitle, tableCommandes, lblVersTitle, tableVersements, dateSummaryBox);
        VBox.setVgrow(tableCommandes, Priority.ALWAYS);

        SplitPane split = new SplitPane(leftBox, rightBox);
        split.setDividerPositions(0.26);

        // Footer avec bouton d'encaissement
        Button btnEncaisser = new Button("💵 Effectuer un versement / Remboursement");
        btnEncaisser.setGraphic(new FontIcon(BootstrapIcons.CASH));
        btnEncaisser.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16; -fx-cursor: hand;");
        btnEncaisser.setOnAction(e -> ouvrirEncaissement());

        Button btnFermer = new Button("Fermer");
        btnFermer.setStyle("-fx-font-size: 12px; -fx-padding: 8 16; -fx-cursor: hand;");
        btnFermer.setOnAction(e -> setResult(true));

        HBox footer = new HBox(12, btnEncaisser, new Separator(Orientation.VERTICAL), btnFermer);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 16, 10, 16));

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(split);
        root.setBottom(footer);

        getDialogPane().setContent(root);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    private void chargerDates() {
        List<LocalDate> dates = ficheDAO.findDatesSortieByClient(client.getId());
        lstDates.getItems().setAll(dates);
        if (!dates.isEmpty()) {
            lstDates.getSelectionModel().select(0);
        } else {
            dataCommandes.clear();
            dataVersements.clear();
        }
    }

    private void chargerDetailsDate(LocalDate date) {
        List<LigneCommande> cmds = ficheDAO.findLignesByClientAndDate(client.getId(), date);
        List<Versement> vers = versementDAO.findByClientAndDate(client.getId(), date);

        dataCommandes.setAll(cmds);
        dataVersements.setAll(vers);

        BigDecimal totCmd = cmds.stream().map(LigneCommande::getMontantHt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totVers = vers.stream().map(Versement::getMontantRemis).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = totCmd.subtract(totVers);

        lblTotalSortiesJour.setText("Commandes du " + FormatUtil.date(date) + " : " + FormatUtil.montant(totCmd) + " FCFA");
        lblTotalVersesJour.setText("Total Versé : " + FormatUtil.montant(totVers) + " FCFA");
        lblResteJour.setText("Solde journée : " + FormatUtil.montant(diff) + " FCFA");
        lblResteJour.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: "
            + (diff.compareTo(BigDecimal.ZERO) > 0 ? "#C62828;" : "#2E7D32;"));
    }

    private void ouvrirEncaissement() {
        TextInputDialog dlg = new TextInputDialog(client.getSoldeActuel().toPlainString());
        dlg.setTitle("Nouveau Versement — " + client.getNom());
        dlg.setHeaderText("Saisissez le montant du remboursement / versement reçu :");
        dlg.setContentText("Montant (FCFA) :");
        dlg.showAndWait().ifPresent(val -> {
            try {
                BigDecimal montant = new BigDecimal(val.trim().replace(" ", "").replace(",", "."));
                Versement v = new Versement();
                v.setNumero(versementDAO.genererNumero());
                v.setDateVersement(LocalDate.now());
                v.setClient(client);
                v.setMontantAttendu(client.getSoldeActuel());
                v.setMontantRemis(montant);
                v.setMontantEnregistre(montant);
                v.setModePaiement("Espèces");
                v.setMotifEcart("Versement client / Remboursement dette");

                caisseService.enregistrerVersement(v);

                // Recharger le solde client actualisé
                client.setSoldeActuel(client.getSoldeActuel().subtract(montant));
                lblSoldeGlobal.setText("Dette accumulée : " + FormatUtil.montant(client.getSoldeActuel()) + " FCFA");
                lblSoldeGlobal.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
                    + (client.getSoldeActuel().compareTo(BigDecimal.ZERO) > 0 ? "#C62828;" : "#2E7D32;"));

                chargerDates();
            } catch (Exception ex) {
                Alert al = new Alert(Alert.AlertType.ERROR, "Montant invalide : " + ex.getMessage());
                al.showAndWait();
            }
        });
    }
}
