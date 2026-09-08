package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.DeblocageService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClientsFxPanel extends FxPanelBase {

    private final ClientDAO       clientDAO  = new ClientDAO();
    private final ProduitDAO      produitDAO = new ProduitDAO();
    private final TarifClientDAO  tarifDAO   = new TarifClientDAO();
    private final AuditDAO        auditDAO   = new AuditDAO();
    private final DeblocageService deblocage = new DeblocageService();
    private final SessionService  session    = SessionService.getInstance();

    // Liste
    private TableView<Client>      table;
    private ObservableList<Client> data = FXCollections.observableArrayList();
    private TextField              searchField;
    private ComboBox<String>       cboStatut;
    private Label                  lblCount;

    // Formulaire inline
    private Client     clientEnCours  = null;
    private boolean    modeNouv       = false;

    private TextField        fCode, fNom, fAdresse, fQuartier, fTel, fEmail, fMotif;
    private ComboBox<CategorieClient> fCat;
    private ComboBox<String> fType;
    private Label            lblFormTitre;
    private VBox             panneauForm;
    private Button           btnSave, btnAnnuler, btnBloquer, btnDebloq, btnSuppr;

    // Catégories
    private ListView<CategorieClient> lstCats;
    private TableView<Client>         tableClientsCat;
    private TableView<ProduitPrix>    tablePrixClient;
    private TextField                 txtRemiseCat;
    private CategorieClient           selectedCat;
    private Client                    selectedClientForTarif;

    public ClientsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    // ══════════════════════════════════════════════════════════════
    //  BUILD UI
    // ══════════════════════════════════════════════════════════════
    private void buildUI() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);

        Tab tabList = new Tab("Liste des Clients", buildListePane());
        tabList.setClosable(false);

        Tab tabCats = new Tab("Catégories & Tarification", buildCategoriesPricing());
        tabCats.setClosable(false);
        tabCats.setOnSelectionChanged(e -> { if (tabCats.isSelected()) refreshCats(); });

        tabs.getTabs().addAll(tabList, tabCats);
        root.setCenter(tabs);
    }

    // ── Onglet Liste ──────────────────────────────────────────────
    private SplitPane buildListePane() {
        SplitPane split = new SplitPane(buildTablePane(), buildFormPane());
        split.setDividerPositions(0.55);
        return split;
    }

    private VBox buildTablePane() {
        searchField = searchField("Rechercher un client…");
        searchField.textProperty().addListener((o, ov, nv) -> refresh());

        cboStatut = new ComboBox<>(FXCollections.observableArrayList("Tous", "Actif", "Bloqué", "Inactif"));
        cboStatut.setValue("Tous");
        cboStatut.setOnAction(e -> refresh());

        Button btnNouv = btnPrimary("+ Nouveau", BootstrapIcons.PERSON_PLUS);
        btnNouv.setOnAction(e -> ouvrirNouveauInline());

        btnBloquer = btnDanger("Bloquer", BootstrapIcons.SLASH_CIRCLE);
        btnBloquer.setOnAction(e -> preparerBlocage());

        btnDebloq = btnSuccess("Débloquer", BootstrapIcons.CHECK_CIRCLE);
        btnDebloq.setOnAction(e -> preparerDeblocage());

        table = styledTable();
        table.setItems(data);
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null && !modeNouv) ouvrirEditionInline(nv);
        });

        TableColumn<Client, String> colCode    = col("Code",         c -> c.getCode());
        TableColumn<Client, String> colNom     = col("Nom",          c -> c.getNom());
        TableColumn<Client, String> colCat     = col("Catégorie",    c -> c.getCategorie() != null ? c.getCategorie().getNom() : "—");
        TableColumn<Client, String> colSolde   = col("Solde (FCFA)", c -> FormatUtil.montant(c.getSoldeActuel()));
        TableColumn<Client, String> colStat    = col("Statut",       c -> c.getStatut().name());

        colStat.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });
        colSolde.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(" ","").replace(",","."));
                    setStyle(v > 0 ? "-fx-text-fill:#D93025;-fx-alignment:CENTER-RIGHT;-fx-font-weight:bold;"
                                   : "-fx-text-fill:#0F9D58;-fx-alignment:CENTER-RIGHT;-fx-font-weight:bold;");
                } catch (Exception ex) { setStyle(""); }
            }
        });

        table.getColumns().addAll(colCode, colNom, colCat, colSolde, colStat);

        lblCount = footerCount("0 client(s)");

        VBox body = new VBox(10);
        body.setPadding(new Insets(10));
        body.getChildren().addAll(
            header("Clients & Livreurs", btnNouv, btnBloquer, btnDebloq,
                new Separator(Orientation.VERTICAL), searchField, cboStatut),
            table, lblCount);
        VBox.setVgrow(table, Priority.ALWAYS);
        body.setFillWidth(true);
        return body;
    }

    private VBox buildFormPane() {
        panneauForm = new VBox(10);
        panneauForm.setPadding(new Insets(14));
        panneauForm.setStyle("-fx-background-color:#FAFAFA; -fx-border-color:#DEDEDE; -fx-border-width:0 0 0 1;");

        lblFormTitre = new Label("Sélectionnez un client");
        lblFormTitre.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1F3A5F;");

        // Champs
        fCode     = fieldReadOnly();
        fNom      = new TextField(); fNom.setPromptText("Nom / Raison sociale *");
        fAdresse  = new TextField(); fAdresse.setPromptText("Adresse");
        fQuartier = new TextField(); fQuartier.setPromptText("Quartier");
        fTel      = new TextField(); fTel.setPromptText("Téléphone");
        fEmail    = new TextField(); fEmail.setPromptText("Email");
        fMotif    = new TextField(); fMotif.setPromptText("Motif (blocage / déblocage)");
        fMotif.setVisible(false); fMotif.setManaged(false);

        List<CategorieClient> cats = clientDAO.findAllCategories();
        fCat = new ComboBox<>(FXCollections.observableArrayList(cats));
        fCat.setMaxWidth(Double.MAX_VALUE);
        fCat.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CategorieClient c) { return c == null ? "" : c.getNom(); }
            @Override public CategorieClient fromString(String s) { return null; }
        });
        if (!cats.isEmpty()) fCat.setValue(cats.get(0));

        fType = new ComboBox<>(FXCollections.observableArrayList("Nominatif", "Anonyme"));
        fType.setValue("Nominatif");
        fType.setMaxWidth(Double.MAX_VALUE);

        btnSave = btnPrimary("Enregistrer", BootstrapIcons.SAVE);
        btnSave.setOnAction(e -> enregistrerClient());
        btnSave.setDisable(true);

        btnAnnuler = btnOutline("Annuler");
        btnAnnuler.setOnAction(e -> annulerForm());
        btnAnnuler.setDisable(true);

        btnSuppr = btnDanger("Supprimer", BootstrapIcons.TRASH);
        btnSuppr.setOnAction(e -> supprimerClient());
        btnSuppr.setDisable(true);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);
        ColumnConstraints cc0 = new ColumnConstraints(); cc0.setPrefWidth(110); cc0.setMinWidth(90);
        ColumnConstraints cc1 = new ColumnConstraints(); cc1.setHgrow(Priority.ALWAYS); cc1.setFillWidth(true);
        grid.getColumnConstraints().addAll(cc0, cc1);

        int r = 0;
        grid.addRow(r++, lbl("Code"),      fCode);
        grid.addRow(r++, lbl("Nom *"),     fNom);
        grid.addRow(r++, lbl("Adresse"),   fAdresse);
        grid.addRow(r++, lbl("Quartier"),  fQuartier);
        grid.addRow(r++, lbl("Téléphone"), fTel);
        grid.addRow(r++, lbl("Email"),     fEmail);
        grid.addRow(r++, lbl("Catégorie"), fCat);
        grid.addRow(r++, lbl("Type"),      fType);
        grid.addRow(r++, lbl("Motif"),     fMotif);

        HBox btnBox = new HBox(8, btnSave, btnAnnuler, btnSuppr);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        panneauForm.getChildren().addAll(lblFormTitre, new Separator(), grid, btnBox);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return panneauForm;
    }

    // ── Onglet Catégories ─────────────────────────────────────────
    private SplitPane buildCategoriesPricing() {
        lstCats = new ListView<>();
        lstCats.setPrefWidth(200);
        lstCats.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CategorieClient item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getNom());
            }
        });
        lstCats.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            selectedCat = nv; chargerDetailCat();
        });

        VBox left = new VBox(10, new Label("Catégories"), lstCats);
        left.setPadding(new Insets(10));

        VBox detail = new VBox(15);
        detail.setPadding(new Insets(10));

        HBox remiseBox = new HBox(10);
        remiseBox.setAlignment(Pos.CENTER_LEFT);
        txtRemiseCat = new TextField();
        txtRemiseCat.setPrefWidth(80);
        Button btnSaveRemise = btnPrimary("Enregistrer %", BootstrapIcons.SAVE);
        btnSaveRemise.setOnAction(e -> saveRemiseCat());
        remiseBox.getChildren().addAll(new Label("Remise par défaut (%) :"), txtRemiseCat, btnSaveRemise);

        tableClientsCat = styledTable();
        TableColumn<Client, String> colClNom = new TableColumn<>("Client");
        colClNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNom()));
        tableClientsCat.getColumns().add(colClNom);
        tableClientsCat.setPrefHeight(200);
        tableClientsCat.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            selectedClientForTarif = nv; chargerPrixClient();
        });

        tablePrixClient = styledTable();
        TableColumn<ProduitPrix, String> colPrNom = new TableColumn<>("Produit");
        colPrNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().produit.getLibelle()));
        TableColumn<ProduitPrix, String> colPrStd = new TableColumn<>("Prix Std");
        colPrStd.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().produit.getPrixUnitaire())));
        TableColumn<ProduitPrix, TextField> colPrSpec = new TableColumn<>("Prix Spécifique");
        colPrSpec.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().fieldPrix));
        tablePrixClient.getColumns().addAll(colPrNom, colPrStd, colPrSpec);

        Button btnSavePrix = btnPrimary("Enregistrer Tarifs Spécifiques", BootstrapIcons.SAVE);
        btnSavePrix.setOnAction(e -> savePrixSpecifiques());

        detail.getChildren().addAll(
            new Label("Configuration de la catégorie"), remiseBox, new Separator(),
            new Label("Clients de la catégorie"), tableClientsCat,
            new Label("Prix personnalisés pour le client sélectionné"), tablePrixClient,
            btnSavePrix);
        VBox.setVgrow(tablePrixClient, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, detail);
        split.setDividerPositions(0.25);
        return split;
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIQUE FORMULAIRE INLINE
    // ══════════════════════════════════════════════════════════════
    private void ouvrirNouveauInline() {
        modeNouv = true;
        clientEnCours = null;
        lblFormTitre.setText("Nouveau client");
        fCode.setText(clientDAO.genererCode());
        fNom.clear(); fAdresse.clear(); fQuartier.clear(); fTel.clear(); fEmail.clear(); fMotif.clear();
        fMotif.setVisible(false); fMotif.setManaged(false);
        List<CategorieClient> cats = clientDAO.findAllCategories();
        fCat.getItems().setAll(cats);
        if (!cats.isEmpty()) fCat.setValue(cats.get(0));
        fType.setValue("Nominatif");
        btnSave.setDisable(false);
        btnAnnuler.setDisable(false);
        btnSuppr.setDisable(true);
        fNom.requestFocus();
        table.getSelectionModel().clearSelection();
    }

    private void ouvrirEditionInline(Client cl) {
        modeNouv = false;
        clientEnCours = cl;
        lblFormTitre.setText("Modifier — " + cl.getNom());
        fCode.setText(cl.getCode());
        fNom.setText(cl.getNom());
        fAdresse.setText(cl.getAdresse() != null ? cl.getAdresse() : "");
        fQuartier.setText(cl.getQuartier() != null ? cl.getQuartier() : "");
        fTel.setText(cl.getTelephone() != null ? cl.getTelephone() : "");
        fEmail.setText(cl.getEmail() != null ? cl.getEmail() : "");
        fMotif.clear();
        fMotif.setVisible(false); fMotif.setManaged(false);
        List<CategorieClient> cats = clientDAO.findAllCategories();
        fCat.getItems().setAll(cats);
        if (cl.getCategorie() != null) {
            cats.stream().filter(c -> c.getId().equals(cl.getCategorie().getId()))
                .findFirst().ifPresent(fCat::setValue);
        } else if (!cats.isEmpty()) fCat.setValue(cats.get(0));
        fType.setValue(cl.getTypeClient() != null ? cl.getTypeClient().name() : "Nominatif");
        btnSave.setDisable(false);
        btnAnnuler.setDisable(false);
        btnSuppr.setDisable(false);
    }

    private void preparerBlocage() {
        Client sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        ouvrirEditionInline(sel);
        fMotif.setVisible(true); fMotif.setManaged(true);
        fMotif.setPromptText("Motif du BLOCAGE *");
        fMotif.setStyle("-fx-border-color: #D93025;");
        lblFormTitre.setText("Bloquer — " + sel.getNom());
        btnSave.setText("Confirmer Blocage");
        btnSave.setStyle("-fx-background-color:#D93025; -fx-text-fill:white;");
        btnSave.setOnAction(e -> confirmerBlocage(sel));
        btnSave.setDisable(false);
        fMotif.requestFocus();
    }

    private void preparerDeblocage() {
        Client sel = table.getSelectionModel().getSelectedItem();
        if (sel == null || !Client.Statut.Bloqué.equals(sel.getStatut())) return;
        ouvrirEditionInline(sel);
        fMotif.setVisible(true); fMotif.setManaged(true);
        fMotif.setPromptText("Motif du DÉBLOCAGE *");
        fMotif.setStyle("-fx-border-color: #0F9D58;");
        lblFormTitre.setText("Débloquer — " + sel.getNom());
        btnSave.setText("Confirmer Déblocage");
        btnSave.setStyle("-fx-background-color:#0F9D58; -fx-text-fill:white;");
        btnSave.setOnAction(e -> confirmerDeblocage(sel));
        btnSave.setDisable(false);
        fMotif.requestFocus();
    }

    private void confirmerBlocage(Client sel) {
        String motif = fMotif.getText().trim();
        if (motif.isEmpty()) { fMotif.setStyle("-fx-border-color:#D93025; -fx-border-width:2;"); return; }
        runAsync(() -> { deblocage.bloquerClient(sel.getId(), motif, sel.getSoldeActuel()); return true; },
            ok -> { refresh(); annulerForm(); });
    }

    private void confirmerDeblocage(Client sel) {
        String motif = fMotif.getText().trim();
        if (motif.isEmpty()) { fMotif.setStyle("-fx-border-color:#0F9D58; -fx-border-width:2;"); return; }
        runAsync(() -> { deblocage.debloquerExceptionnel(sel.getId(), motif, "", null, null); return true; },
            ok -> { refresh(); annulerForm(); });
    }

    private void enregistrerClient() {
        String nom = fNom.getText().trim();
        if (nom.isEmpty()) { fNom.setStyle("-fx-border-color:#D93025; -fx-border-width:2;"); return; }
        fNom.setStyle("");

        Client nc = clientEnCours != null ? clientEnCours : new Client();
        nc.setCode(fCode.getText().trim());
        nc.setNom(nom);
        nc.setAdresse(fAdresse.getText().trim());
        nc.setQuartier(fQuartier.getText().trim());
        nc.setTelephone(fTel.getText().trim());
        nc.setEmail(fEmail.getText().trim());
        nc.setCategorie(fCat.getValue());
        try { nc.setTypeClient(Client.TypeClient.valueOf(fType.getValue())); }
        catch (Exception ex) { nc.setTypeClient(Client.TypeClient.Nominatif); }
        nc.setEstAnonyme(Client.TypeClient.Anonyme.equals(nc.getTypeClient()));

        if (nc.getCategorie() == null) {
            List<CategorieClient> cats = clientDAO.findAllCategories();
            if (!cats.isEmpty()) nc.setCategorie(cats.get(0));
        }

        runAsync(() -> {
            boolean isNew = (nc.getId() == null);
            if (isNew) { String id = clientDAO.save(nc); nc.setId(id); }
            else clientDAO.update(nc);
            auditDAO.log(new JournalAudit("Client", nc.getId(),
                isNew ? JournalAudit.CREATE : JournalAudit.UPDATE,
                session.getUserId(), session.getLogin(), nc.getCode() + " — " + nc.getNom()));
            return nc;
        }, saved -> { refresh(); annulerForm(); });
    }

    private void supprimerClient() {
        if (clientEnCours == null) return;
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Supprimer définitivement " + clientEnCours.getNom() + " ?",
            ButtonType.YES, ButtonType.NO);
        conf.setHeaderText(null);
        conf.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            Client toDelete = clientEnCours;
            runAsync(() -> { clientDAO.delete(toDelete.getId()); return true; },
                ok -> { refresh(); annulerForm(); });
        });
    }

    private void annulerForm() {
        clientEnCours = null; modeNouv = false;
        lblFormTitre.setText("Sélectionnez un client");
        fCode.clear(); fNom.clear(); fAdresse.clear(); fQuartier.clear();
        fTel.clear(); fEmail.clear(); fMotif.clear();
        fMotif.setVisible(false); fMotif.setManaged(false);
        fNom.setStyle(""); fMotif.setStyle("");
        btnSave.setDisable(true); btnAnnuler.setDisable(true); btnSuppr.setDisable(true);
        btnSave.setText("Enregistrer");
        btnSave.setStyle("");
        btnSave.setOnAction(e -> enregistrerClient());
        table.getSelectionModel().clearSelection();
    }

    // ══════════════════════════════════════════════════════════════
    //  CATÉGORIES / TARIFS
    // ══════════════════════════════════════════════════════════════
    private void refreshCats() {
        runAsync(() -> clientDAO.findAllCategories(), cats -> {
            lstCats.getItems().setAll(cats);
            if (!cats.isEmpty()) lstCats.getSelectionModel().select(0);
        });
    }

    private void chargerDetailCat() {
        if (selectedCat == null) return;
        txtRemiseCat.setText(selectedCat.getPourcentageRemise() != null ? selectedCat.getPourcentageRemise().toString() : "0");
        runAsync(() -> clientDAO.search(null, selectedCat.getId(), null, false), clients -> {
            tableClientsCat.getItems().setAll(clients);
            if (!clients.isEmpty()) tableClientsCat.getSelectionModel().select(0);
            else tablePrixClient.getItems().clear();
        });
    }

    private void chargerPrixClient() {
        if (selectedClientForTarif == null) { tablePrixClient.getItems().clear(); return; }
        runAsync(() -> {
            List<Produit> produits = produitDAO.findAll(false);
            return produits.stream().map(p -> {
                BigDecimal prixSpec = tarifDAO.findPrixSpecifique(
                    selectedClientForTarif.getId(), p.getId(), LocalDate.now()).orElse(null);
                return new ProduitPrix(p, prixSpec);
            }).collect(Collectors.toList());
        }, list -> tablePrixClient.getItems().setAll(list));
    }

    private void saveRemiseCat() {
        if (selectedCat == null) return;
        try {
            BigDecimal remise = new BigDecimal(txtRemiseCat.getText().trim().replace(",", "."));
            selectedCat.setPourcentageRemise(remise);
            runAsync(() -> { clientDAO.updateCategorie(selectedCat); return true; },
                ok -> lblFormTitre.setText("Remise mise à jour"));
        } catch (Exception ex) {
            txtRemiseCat.setStyle("-fx-border-color:#D93025; -fx-border-width:2;");
        }
    }

    private void savePrixSpecifiques() {
        if (selectedClientForTarif == null) return;
        runAsync(() -> {
            for (ProduitPrix pp : tablePrixClient.getItems()) {
                String val = pp.fieldPrix.getText().trim();
                if (val.isEmpty() || val.equals("0")) continue;
                try {
                    BigDecimal prix = new BigDecimal(val.replace(",", "."));
                    tarifDAO.save(selectedClientForTarif.getId(), pp.produit.getId(), prix, LocalDate.now(), null);
                } catch (Exception ignored) {}
            }
            return true;
        }, ok -> chargerPrixClient());
    }

    // ══════════════════════════════════════════════════════════════
    //  REFRESH
    // ══════════════════════════════════════════════════════════════
    @Override
    public void refresh() {
        String txt  = searchField != null ? searchField.getText() : null;
        String stat = cboStatut != null && !"Tous".equals(cboStatut.getValue()) ? cboStatut.getValue() : null;
        runAsync(() -> clientDAO.search(txt, null, stat, false), list -> {
            data.setAll(list);
            lblCount.setText(list.size() + " client(s)");
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════
    private <T> TableColumn<Client, String> col(String titre, Function<Client, String> fn) {
        TableColumn<Client, String> c = new TableColumn<>(titre);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return c;
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold; -fx-text-fill:#3C4043; -fx-font-size:12px;");
        return l;
    }

    private TextField fieldReadOnly() {
        TextField tf = new TextField();
        tf.setEditable(false);
        tf.setStyle("-fx-background-color:#F1F3F4; -fx-text-fill:#3C4043; -fx-font-weight:bold;");
        return tf;
    }

    // ── Modèle interne ProduitPrix ────────────────────────────────
    private static class ProduitPrix {
        Produit produit;
        TextField fieldPrix;
        ProduitPrix(Produit p, BigDecimal prixSpec) {
            this.produit = p;
            this.fieldPrix = new TextField(prixSpec != null ? prixSpec.toPlainString() : "");
            this.fieldPrix.setPromptText("Auto");
        }
    }
}
