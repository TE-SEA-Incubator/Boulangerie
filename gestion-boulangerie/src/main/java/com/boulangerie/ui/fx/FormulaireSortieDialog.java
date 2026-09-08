package com.boulangerie.ui.fx;

import com.boulangerie.dao.ClientDAO;
import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.dao.TarifClientDAO;
import com.boulangerie.model.Client;
import com.boulangerie.model.FicheJournaliere;
import com.boulangerie.model.LigneCommande;
import com.boulangerie.model.Produit;
import com.boulangerie.util.FormatUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Dialogue complet et moderne de saisie / modification d'une ligne de sortie.
 * Inclut des ComboBox filtrables (recherche par nom) et des calculs en temps réel.
 */
public class FormulaireSortieDialog extends Dialog<Boolean> {

    private final FicheJournaliere fiche;
    private final LigneCommande lineToEdit;

    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final TarifClientDAO tarifDAO = new TarifClientDAO();

    // Données sources complètes
    private ObservableList<Client> allClients;
    private ObservableList<Produit> allProduits;

    // Composants filtrage client
    private TextField txtSearchClient;
    private ListView<Client> listViewClients;
    private Label lblSelectedClient;
    private Client selectedClient;

    // Composants filtrage produit
    private TextField txtSearchProduit;
    private ListView<Produit> listViewProduits;
    private Label lblSelectedProduit;
    private Produit selectedProduit;

    // Quantités
    private Spinner<Integer> spinSortie;
    private Spinner<Integer> spinRetour;
    private Label lblQteNette;
    private ProgressBar barQteRatio;

    // Tarification
    private TextField txtTarif;
    private TextField txtRemise;

    // Résultat
    private Label lblMontantHt;
    private Label lblMontantBrut;

    // Motif
    private TextArea txtMotifRetour;

    public FormulaireSortieDialog(FicheJournaliere fiche, LigneCommande lineToEdit) {
        this.fiche = fiche;
        this.lineToEdit = lineToEdit;

        setTitle(lineToEdit == null
            ? "✚ Nouvelle Sortie — " + fiche.getNumero()
            : "✏ Modifier la Sortie — " + fiche.getNumero());
        setHeaderText(null);
        getDialogPane().setPrefWidth(680);
        getDialogPane().setPrefHeight(720);

        buildUI();
        initialiserDonnees();
    }

    private void buildUI() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(12));

        // ═══════════════════════════════════════════
        // SECTION 1 — Sélection du Client (filtrable)
        // ═══════════════════════════════════════════
        TitledPane sectionClient = new TitledPane();
        sectionClient.setText("👤  Client / Livreur");
        sectionClient.setExpanded(true);
        sectionClient.setCollapsible(true);

        VBox clientBox = new VBox(6);
        clientBox.setPadding(new Insets(8));

        txtSearchClient = new TextField();
        txtSearchClient.setPromptText("🔍 Tapez un nom, code ou quartier pour filtrer...");
        txtSearchClient.setStyle("-fx-font-size: 13px; -fx-padding: 8;");

        listViewClients = new ListView<>();
        listViewClients.setPrefHeight(120);
        listViewClients.setStyle("-fx-font-size: 12px;");
        listViewClients.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Client c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String cat = c.getCategorie() != null ? c.getCategorie().getNom() : "";
                    String solde = c.getSoldeActuel() != null ? FormatUtil.montant(c.getSoldeActuel()) : "0";
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Label name = new Label(c.getCode() + " — " + c.getNom());
                    name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                    Label catLbl = new Label(cat);
                    catLbl.setStyle("-fx-background-color: #E8F0FE; -fx-text-fill: #1A73E8; "
                        + "-fx-padding: 1 6; -fx-background-radius: 4; -fx-font-size: 10px;");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Label soldeLbl = new Label("Solde: " + solde + " F");
                    soldeLbl.setStyle("-fx-text-fill: #5F6368; -fx-font-size: 11px;");
                    row.getChildren().addAll(name, catLbl, spacer, soldeLbl);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        lblSelectedClient = new Label("Aucun client sélectionné");
        lblSelectedClient.setStyle("-fx-font-size: 12px; -fx-text-fill: #C62828; -fx-font-style: italic;");

        listViewClients.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                selectedClient = nv;
                lblSelectedClient.setText("✅ " + nv.getCode() + " — " + nv.getNom());
                lblSelectedClient.setStyle("-fx-font-size: 13px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                sectionClient.setExpanded(false);
                mettreAJourTarif();
            }
        });

        clientBox.getChildren().addAll(txtSearchClient, listViewClients, lblSelectedClient);
        sectionClient.setContent(clientBox);

        // ═══════════════════════════════════════════
        // SECTION 2 — Sélection du Produit (filtrable)
        // ═══════════════════════════════════════════
        TitledPane sectionProduit = new TitledPane();
        sectionProduit.setText("📦  Produit");
        sectionProduit.setExpanded(true);
        sectionProduit.setCollapsible(true);

        VBox produitBox = new VBox(6);
        produitBox.setPadding(new Insets(8));

        txtSearchProduit = new TextField();
        txtSearchProduit.setPromptText("🔍 Tapez un nom ou code produit...");
        txtSearchProduit.setStyle("-fx-font-size: 13px; -fx-padding: 8;");

        listViewProduits = new ListView<>();
        listViewProduits.setPrefHeight(100);
        listViewProduits.setStyle("-fx-font-size: 12px;");
        listViewProduits.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Produit p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Label name = new Label(p.getCode() + " — " + p.getLibelle());
                    name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    String px = p.getPrixUnitaire() != null ? FormatUtil.montant(p.getPrixUnitaire()) : "0";
                    Label prix = new Label(px + " FCFA");
                    prix.setStyle("-fx-text-fill: #1F3A5F; -fx-font-weight: bold; -fx-font-size: 12px;");
                    row.getChildren().addAll(name, spacer, prix);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        lblSelectedProduit = new Label("Aucun produit sélectionné");
        lblSelectedProduit.setStyle("-fx-font-size: 12px; -fx-text-fill: #C62828; -fx-font-style: italic;");

        listViewProduits.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                selectedProduit = nv;
                String px = nv.getPrixUnitaire() != null ? FormatUtil.montant(nv.getPrixUnitaire()) : "0";
                lblSelectedProduit.setText("✅ " + nv.getLibelle() + "  (" + px + " FCFA/unité)");
                lblSelectedProduit.setStyle("-fx-font-size: 13px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                sectionProduit.setExpanded(false);
                mettreAJourTarif();
            }
        });

        produitBox.getChildren().addAll(txtSearchProduit, listViewProduits, lblSelectedProduit);
        sectionProduit.setContent(produitBox);

        // ═══════════════════════════════════════════
        // SECTION 3 — Quantités
        // ═══════════════════════════════════════════
        TitledPane sectionQte = new TitledPane();
        sectionQte.setText("📊  Quantités & Tarification");
        sectionQte.setExpanded(true);
        sectionQte.setCollapsible(false);

        GridPane gridQte = new GridPane();
        gridQte.setHgap(16);
        gridQte.setVgap(10);
        gridQte.setPadding(new Insets(10));

        spinSortie = new Spinner<>(0, 99999, 0, 1);
        spinSortie.setEditable(true);
        spinSortie.setPrefWidth(130);
        spinSortie.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

        spinRetour = new Spinner<>(0, 99999, 0, 1);
        spinRetour.setEditable(true);
        spinRetour.setPrefWidth(130);
        spinRetour.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

        lblQteNette = new Label("0");
        lblQteNette.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");

        barQteRatio = new ProgressBar(0);
        barQteRatio.setPrefWidth(180);
        barQteRatio.setStyle("-fx-accent: #2E7D32;");

        // Tarif
        txtTarif = new TextField("0");
        txtTarif.setPrefWidth(140);
        txtTarif.setStyle("-fx-font-size: 13px;");

        txtRemise = new TextField("0");
        txtRemise.setPrefWidth(90);
        txtRemise.setStyle("-fx-font-size: 13px;");

        // Labels résultat
        lblMontantBrut = new Label("Brut : 0 FCFA");
        lblMontantBrut.setStyle("-fx-font-size: 12px; -fx-text-fill: #5F6368;");

        lblMontantHt = new Label("0 FCFA");
        lblMontantHt.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");

        gridQte.addRow(0, bold("Qté Sortie *"), spinSortie, bold("Qté Retour"), spinRetour);
        gridQte.addRow(1, bold("Qté Nette"), lblQteNette, new Label(""), barQteRatio);
        gridQte.add(new Separator(), 0, 2, 4, 1);
        gridQte.addRow(3, bold("Prix Unit. (FCFA) *"), txtTarif, bold("Remise (%)"), txtRemise);
        gridQte.add(new Separator(), 0, 4, 4, 1);

        HBox montantBox = new HBox(16);
        montantBox.setAlignment(Pos.CENTER_LEFT);
        montantBox.setPadding(new Insets(6, 0, 0, 0));
        VBox montantVBox = new VBox(2,
            lblMontantBrut,
            new HBox(8, bold("Montant Total HT :"), lblMontantHt)
        );
        montantBox.getChildren().add(montantVBox);
        gridQte.add(montantBox, 0, 5, 4, 1);

        sectionQte.setContent(gridQte);

        // ═══════════════════════════════════════════
        // SECTION 4 — Motif de retour
        // ═══════════════════════════════════════════
        txtMotifRetour = new TextArea();
        txtMotifRetour.setPromptText("Ex: Pain rassis, avarie, invendu, impayé...");
        txtMotifRetour.setPrefRowCount(2);
        txtMotifRetour.setWrapText(true);

        TitledPane sectionMotif = new TitledPane("📝  Motif / Remarques", txtMotifRetour);
        sectionMotif.setExpanded(false);
        sectionMotif.setCollapsible(true);

        content.getChildren().addAll(sectionClient, sectionProduit, sectionQte, sectionMotif);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        getDialogPane().setContent(scrollPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style du bouton OK
        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setText("✅ Enregistrer");
            okBtn.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20;");
        }

        // ── Listeners pour calcul en direct ──
        spinSortie.valueProperty().addListener((o, ov, nv) -> recalculer());
        spinRetour.valueProperty().addListener((o, ov, nv) -> recalculer());
        txtTarif.textProperty().addListener((o, ov, nv) -> recalculer());
        txtRemise.textProperty().addListener((o, ov, nv) -> recalculer());

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return false;
            return enregistrer();
        });
    }

    private Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        return l;
    }

    private void initialiserDonnees() {
        List<Client> clients = clientDAO.findAll();
        List<Produit> produits = produitDAO.findAll(false);

        allClients = FXCollections.observableArrayList(clients);
        allProduits = FXCollections.observableArrayList(produits);

        // FilteredList pour clients
        FilteredList<Client> filteredClients = new FilteredList<>(allClients, p -> true);
        listViewClients.setItems(filteredClients);

        txtSearchClient.textProperty().addListener((obs, ov, nv) -> {
            String filter = nv == null ? "" : nv.toLowerCase().trim();
            filteredClients.setPredicate(c -> {
                if (filter.isEmpty()) return true;
                if (c.getNom() != null && c.getNom().toLowerCase().contains(filter)) return true;
                if (c.getCode() != null && c.getCode().toLowerCase().contains(filter)) return true;
                if (c.getQuartier() != null && c.getQuartier().toLowerCase().contains(filter)) return true;
                if (c.getCategorie() != null && c.getCategorie().getNom() != null
                    && c.getCategorie().getNom().toLowerCase().contains(filter)) return true;
                return false;
            });
        });

        // FilteredList pour produits
        FilteredList<Produit> filteredProduits = new FilteredList<>(allProduits, p -> true);
        listViewProduits.setItems(filteredProduits);

        txtSearchProduit.textProperty().addListener((obs, ov, nv) -> {
            String filter = nv == null ? "" : nv.toLowerCase().trim();
            filteredProduits.setPredicate(p -> {
                if (filter.isEmpty()) return true;
                if (p.getLibelle() != null && p.getLibelle().toLowerCase().contains(filter)) return true;
                if (p.getCode() != null && p.getCode().toLowerCase().contains(filter)) return true;
                return false;
            });
        });

        // Pré-remplissage en mode modification
        if (lineToEdit != null) {
            if (lineToEdit.getClient() != null) {
                clients.stream()
                    .filter(c -> c.getId().equals(lineToEdit.getClient().getId()))
                    .findFirst()
                    .ifPresent(c -> {
                        selectedClient = c;
                        listViewClients.getSelectionModel().select(c);
                        listViewClients.scrollTo(c);
                        lblSelectedClient.setText("✅ " + c.getCode() + " — " + c.getNom());
                        lblSelectedClient.setStyle("-fx-font-size: 13px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                    });
            }
            if (lineToEdit.getProduit() != null) {
                produits.stream()
                    .filter(p -> p.getId().equals(lineToEdit.getProduit().getId()))
                    .findFirst()
                    .ifPresent(p -> {
                        selectedProduit = p;
                        listViewProduits.getSelectionModel().select(p);
                        listViewProduits.scrollTo(p);
                        String px = p.getPrixUnitaire() != null ? FormatUtil.montant(p.getPrixUnitaire()) : "0";
                        lblSelectedProduit.setText("✅ " + p.getLibelle() + "  (" + px + " FCFA/unité)");
                        lblSelectedProduit.setStyle("-fx-font-size: 13px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                    });
            }
            spinSortie.getValueFactory().setValue(lineToEdit.getQuantiteSortie());
            spinRetour.getValueFactory().setValue(lineToEdit.getQuantiteRetournee());
            txtTarif.setText(lineToEdit.getTarifApplicable() != null ? lineToEdit.getTarifApplicable().toPlainString() : "0");
            txtRemise.setText(lineToEdit.getRemisePct() != null ? lineToEdit.getRemisePct().toPlainString() : "0");
            txtMotifRetour.setText(lineToEdit.getMotifRetour() != null ? lineToEdit.getMotifRetour() : "");
            recalculer();
        }

        // Focus initial sur la recherche client
        Platform.runLater(() -> txtSearchClient.requestFocus());
    }

    private void mettreAJourTarif() {
        if (selectedProduit == null) return;

        BigDecimal price = selectedProduit.getPrixUnitaire();
        if (selectedClient != null && selectedClient.getId() != null) {
            LocalDate date = fiche.getDateFiche() != null ? fiche.getDateFiche() : LocalDate.now();
            price = tarifDAO.findPrixSpecifique(selectedClient.getId(), selectedProduit.getId(), date)
                .orElse(selectedProduit.getPrixUnitaire());
        }
        txtTarif.setText(price != null ? price.toPlainString() : "0");
        recalculer();
    }

    private void recalculer() {
        try {
            int qteSortie = spinSortie.getValue();
            int qteRetour = spinRetour.getValue();
            int qteNette = Math.max(0, qteSortie - qteRetour);
            lblQteNette.setText(String.valueOf(qteNette));

            // Barre de ratio retour
            if (qteSortie > 0) {
                double ratio = (double) qteNette / qteSortie;
                barQteRatio.setProgress(ratio);
                if (ratio < 0.5) {
                    barQteRatio.setStyle("-fx-accent: #C62828;");
                } else if (ratio < 0.8) {
                    barQteRatio.setStyle("-fx-accent: #F5A623;");
                } else {
                    barQteRatio.setStyle("-fx-accent: #2E7D32;");
                }
            } else {
                barQteRatio.setProgress(0);
            }

            BigDecimal tarif = parseBigDecimal(txtTarif.getText());
            BigDecimal remise = parseBigDecimal(txtRemise.getText());

            BigDecimal brut = tarif.multiply(BigDecimal.valueOf(qteNette));
            lblMontantBrut.setText("Brut : " + FormatUtil.montant(brut) + " FCFA");

            BigDecimal montant;
            if (remise.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal facteur = BigDecimal.ONE.subtract(remise.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                montant = brut.multiply(facteur).setScale(2, RoundingMode.HALF_UP);
            } else {
                montant = brut.setScale(2, RoundingMode.HALF_UP);
            }

            lblMontantHt.setText(FormatUtil.montant(montant) + " FCFA");

            if (montant.compareTo(BigDecimal.ZERO) > 0) {
                lblMontantHt.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
            } else {
                lblMontantHt.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #C62828;");
            }
        } catch (Exception e) {
            lblQteNette.setText("0");
            lblMontantBrut.setText("Brut : 0 FCFA");
            lblMontantHt.setText("0 FCFA");
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        try {
            return new BigDecimal(text.trim().replace(" ", "").replace(",", "."));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean enregistrer() {
        if (selectedClient == null || selectedProduit == null) {
            Alert al = new Alert(Alert.AlertType.WARNING,
                "Veuillez sélectionner un client et un produit.\n\nUtilisez les champs de recherche pour trouver rapidement.");
            al.setHeaderText("Sélection incomplète");
            al.showAndWait();
            return false;
        }

        try {
            int qteSortie = spinSortie.getValue();
            int qteRetour = spinRetour.getValue();
            BigDecimal tarif = parseBigDecimal(txtTarif.getText());
            BigDecimal remise = parseBigDecimal(txtRemise.getText());

            if (qteSortie < 0 || qteRetour < 0) {
                new Alert(Alert.AlertType.WARNING, "Les quantités ne peuvent pas être négatives.").showAndWait();
                return false;
            }
            if (qteSortie == 0 && qteRetour == 0) {
                new Alert(Alert.AlertType.WARNING, "La quantité de sortie ne peut pas être 0.").showAndWait();
                return false;
            }

            // Calcul du montant HT
            int qteNette = Math.max(0, qteSortie - qteRetour);
            BigDecimal brut = tarif.multiply(BigDecimal.valueOf(qteNette));
            BigDecimal montantHt;
            if (remise.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal facteur = BigDecimal.ONE.subtract(remise.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                montantHt = brut.multiply(facteur).setScale(2, RoundingMode.HALF_UP);
            } else {
                montantHt = brut.setScale(2, RoundingMode.HALF_UP);
            }

            LigneCommande l = lineToEdit != null ? lineToEdit : new LigneCommande();
            l.setFicheId(fiche.getId());
            l.setClient(selectedClient);
            l.setProduit(selectedProduit);
            l.setQuantiteSortie(qteSortie);
            l.setQuantiteRetournee(qteRetour);
            l.setTarifApplicable(tarif);
            l.setTypeTarif("Standard");
            l.setRemisePct(remise);
            l.setMontantHt(montantHt);
            l.setMotifRetour(txtMotifRetour.getText() != null ? txtMotifRetour.getText().trim() : "");

            if (lineToEdit == null) {
                ficheDAO.saveLigne(l);
            } else {
                ficheDAO.updateLigne(l);
            }

            ficheDAO.recalculerTotauxFiche(fiche.getId());
            return true;

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Erreur lors de l'enregistrement :\n" + ex.getMessage()).showAndWait();
            return false;
        }
    }
}
