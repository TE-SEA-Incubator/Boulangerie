package com.boulangerie.ui.fx;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
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

import java.math.BigDecimal;
import java.util.List;

public class ProduitsFxPanel extends FxPanelBase {

    private final ProduitDAO   produitDAO = new ProduitDAO();
    private final AuditDAO     auditDAO   = new AuditDAO();
    private final SessionService session  = SessionService.getInstance();

    // Table
    private TableView<Produit>      table;
    private ObservableList<Produit> data = FXCollections.observableArrayList();
    private TextField               searchField;
    private ComboBox<Famille>       cboFamille;
    private Label                   lblCount;

    // Formulaire inline
    private Produit   produitEnCours = null;
    private boolean   modeNouv       = false;

    private TextField       fCode, fLib, fPrix, fUnite, fSeuil;
    private TextArea        fDesc;
    private ComboBox<Famille> fFamille;
    private ComboBox<String>  fStatut;
    private Label             lblFormTitre;
    private Button            btnSave, btnAnnuler, btnSuppr;

    public ProduitsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    // ══════════════════════════════════════════════════════════════
    //  BUILD UI
    // ══════════════════════════════════════════════════════════════
    private void buildUI() {
        SplitPane split = new SplitPane(buildTablePane(), buildFormPane());
        split.setDividerPositions(0.58);

        // Image pain + titre
        var imgPain = loadImage("assets/Image 1 1.png", 42, 42);
        HBox titreRow = new HBox(10);
        titreRow.setAlignment(Pos.CENTER_LEFT);
        if (imgPain != null) titreRow.getChildren().add(imgPain);
        Label titreLabel = sectionTitle("Catalogue des Produits");
        titreRow.getChildren().add(titreLabel);

        Region accent = new Region();
        accent.setStyle("-fx-background-color:#F5A623; -fx-pref-height:3; -fx-max-height:3; -fx-background-radius:2; -fx-pref-width:44;");

        VBox wrapper = new VBox(6, titreRow, accent, split);
        wrapper.setFillWidth(true);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setCenter(wrapper);

        chargerFamilles(null);
    }

    // ── Tableau gauche ────────────────────────────────────────────
    private VBox buildTablePane() {
        searchField = searchField("Rechercher un produit…");
        searchField.textProperty().addListener((o, ov, nv) -> appliquerFiltres());

        cboFamille = new ComboBox<>();
        cboFamille.setPromptText("Toutes les familles");
        cboFamille.setStyle("-fx-pref-width:160;");
        cboFamille.setOnAction(e -> appliquerFiltres());

        Button btnNouv = btnPrimary("+ Nouveau", BootstrapIcons.PLUS_CIRCLE);
        btnNouv.setOnAction(e -> ouvrirNouveauInline());

        table = styledTable();
        table.setItems(data);
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null && !modeNouv) ouvrirEditionInline(nv);
        });

        TableColumn<Produit, String> colCode   = new TableColumn<>("Code");
        TableColumn<Produit, String> colLib    = new TableColumn<>("Libellé");
        TableColumn<Produit, String> colFam    = new TableColumn<>("Famille");
        TableColumn<Produit, String> colPrix   = new TableColumn<>("Prix (FCFA)");
        TableColumn<Produit, String> colStatut = new TableColumn<>("Statut");

        colCode  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCode()));
        colLib   .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLibelle()));
        colFam   .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getFamille() != null ? d.getValue().getFamille().getNom() : "—"));
        colPrix  .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getPrixUnitaire() != null ? FormatUtil.montant(d.getValue().getPrixUnitaire()) : "0"));
        colPrix.setStyle("-fx-font-weight:bold; -fx-text-fill:#1F3A5F;");
        colStatut.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getStatut() != null ? d.getValue().getStatut().name() : "—"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });

        table.getColumns().addAll(colCode, colLib, colFam, colPrix, colStatut);

        lblCount = footerCount("0 produits");

        HBox toolbar = toolbar(btnNouv,
            new Separator(Orientation.VERTICAL), searchField, cboFamille);

        VBox body = new VBox(10, toolbar, table, lblCount);
        body.setPadding(new Insets(10));
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        return body;
    }

    // ── Formulaire droit ──────────────────────────────────────────
    private VBox buildFormPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(14));
        pane.setStyle("-fx-background-color:#FAFAFA; -fx-border-color:#DEDEDE; -fx-border-width:0 0 0 1;");

        lblFormTitre = new Label("Sélectionnez un produit");
        lblFormTitre.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1F3A5F;");

        // Champs
        fCode  = fieldReadOnly();
        fLib   = new TextField(); fLib.setPromptText("Libellé du produit *");
        fPrix  = new TextField("0"); fPrix.setPromptText("Prix unitaire (FCFA) *");
        fUnite = new TextField("Pièce"); fUnite.setPromptText("Unité *");
        fSeuil = new TextField("0"); fSeuil.setPromptText("Seuil alerte");
        fDesc  = new TextArea(); fDesc.setPromptText("Description"); fDesc.setPrefRowCount(3);

        // Famille avec bouton ajout inline
        fFamille = new ComboBox<>();
        fFamille.setMaxWidth(Double.MAX_VALUE);
        fFamille.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Famille f) { return f == null ? "" : f.getNom(); }
            @Override public Famille fromString(String s) { return null; }
        });
        fFamille.setOnAction(e -> {
            if (modeNouv && fFamille.getValue() != null)
                fCode.setText(produitDAO.genererCode(fFamille.getValue().getId()));
        });

        TextField txtNouvFam = new TextField();
        txtNouvFam.setPromptText("Nouvelle famille…");
        txtNouvFam.setMaxWidth(Double.MAX_VALUE);

        Button btnAjoutFam = btnOutline("+ Ajouter");
        btnAjoutFam.setOnAction(e -> {
            String nom = txtNouvFam.getText().trim();
            if (nom.isEmpty()) return;
            try {
                Famille nf = produitDAO.saveFamille(nom);
                fFamille.getItems().add(nf);
                fFamille.setValue(nf);
                chargerFamilles(nf.getId());
                if (modeNouv) fCode.setText(produitDAO.genererCode(nf.getId()));
                txtNouvFam.clear();
            } catch (Exception ex) {
                txtNouvFam.setStyle("-fx-border-color:#D93025;");
            }
        });

        HBox famNouvBox = new HBox(6, txtNouvFam, btnAjoutFam);
        HBox.setHgrow(txtNouvFam, Priority.ALWAYS);

        fStatut = new ComboBox<>(FXCollections.observableArrayList("Actif", "Inactif"));
        fStatut.setValue("Actif");
        fStatut.setMaxWidth(Double.MAX_VALUE);

        // Boutons action
        btnSave = btnPrimary("Enregistrer", BootstrapIcons.SAVE);
        btnSave.setOnAction(e -> enregistrerProduit());
        btnSave.setDisable(true);

        btnAnnuler = btnOutline("Annuler");
        btnAnnuler.setOnAction(e -> annulerForm());
        btnAnnuler.setDisable(true);

        btnSuppr = btnDanger("Supprimer", BootstrapIcons.TRASH);
        btnSuppr.setOnAction(e -> supprimerProduit());
        btnSuppr.setDisable(true);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);
        ColumnConstraints cc0 = new ColumnConstraints(); cc0.setPrefWidth(120); cc0.setMinWidth(90);
        ColumnConstraints cc1 = new ColumnConstraints(); cc1.setHgrow(Priority.ALWAYS); cc1.setFillWidth(true);
        grid.getColumnConstraints().addAll(cc0, cc1);

        int r = 0;
        grid.addRow(r++, lbl("Code (auto)"),    fCode);
        grid.addRow(r++, lbl("Libellé *"),      fLib);
        grid.addRow(r++, lbl("Famille *"),       fFamille);
        grid.addRow(r++, lbl("Nouvelle fam."),  famNouvBox);
        grid.addRow(r++, lbl("Prix (FCFA) *"),  fPrix);
        grid.addRow(r++, lbl("Unité *"),        fUnite);
        grid.addRow(r++, lbl("Seuil alerte"),   fSeuil);
        grid.addRow(r++, lbl("Statut"),         fStatut);
        grid.addRow(r++, lbl("Description"),    fDesc);

        HBox btnBox = new HBox(8, btnSave, btnAnnuler, btnSuppr);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        pane.getChildren().addAll(lblFormTitre, new Separator(), grid, btnBox);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return pane;
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIQUE INLINE
    // ══════════════════════════════════════════════════════════════
    private void ouvrirNouveauInline() {
        modeNouv = true;
        produitEnCours = null;
        lblFormTitre.setText("Nouveau produit");
        List<Famille> familles = produitDAO.findAllFamilles();
        fFamille.getItems().setAll(familles);
        if (!familles.isEmpty()) {
            fFamille.setValue(familles.get(0));
            fCode.setText(produitDAO.genererCode(familles.get(0).getId()));
        } else {
            fCode.clear();
        }
        fLib.clear(); fPrix.setText("0"); fUnite.setText("Pièce"); fSeuil.setText("0"); fDesc.clear();
        fStatut.setValue("Actif");
        btnSave.setDisable(false); btnAnnuler.setDisable(false); btnSuppr.setDisable(true);
        fLib.requestFocus();
        table.getSelectionModel().clearSelection();
    }

    private void ouvrirEditionInline(Produit p) {
        modeNouv = false;
        produitEnCours = p;
        lblFormTitre.setText("Modifier — " + p.getCode());
        fCode.setText(p.getCode());
        fLib.setText(p.getLibelle());
        fPrix.setText(p.getPrixUnitaire() != null ? p.getPrixUnitaire().toPlainString() : "0");
        fUnite.setText(p.getUnite() != null ? p.getUnite() : "Pièce");
        fSeuil.setText(String.valueOf(p.getSeuilAlerte()));
        fDesc.setText(p.getDescription() != null ? p.getDescription() : "");
        fStatut.setValue(p.getStatut() != null ? p.getStatut().name() : "Actif");

        List<Famille> familles = produitDAO.findAllFamilles();
        fFamille.getItems().setAll(familles);
        if (p.getFamille() != null) {
            familles.stream().filter(f -> f.getId().equals(p.getFamille().getId()))
                .findFirst().ifPresent(fFamille::setValue);
        } else if (!familles.isEmpty()) {
            fFamille.setValue(familles.get(0));
        }

        btnSave.setDisable(false); btnAnnuler.setDisable(false); btnSuppr.setDisable(false);
        fLib.setStyle(""); fPrix.setStyle("");
    }

    private void enregistrerProduit() {
        String lib = fLib.getText().trim();
        if (lib.isEmpty()) { fLib.setStyle("-fx-border-color:#D93025; -fx-border-width:2;"); return; }
        fLib.setStyle("");

        BigDecimal prix;
        try {
            prix = new BigDecimal(fPrix.getText().trim().replace(',', '.'));
            if (prix.signum() < 0) throw new NumberFormatException();
            fPrix.setStyle("");
        } catch (Exception ex) {
            fPrix.setStyle("-fx-border-color:#D93025; -fx-border-width:2;");
            return;
        }

        Produit np = produitEnCours != null ? produitEnCours : new Produit();
        np.setCode(fCode.getText().trim());
        np.setLibelle(lib);
        np.setFamille(fFamille.getValue());
        np.setPrixUnitaire(prix);
        np.setUnite(fUnite.getText().trim().isEmpty() ? "Pièce" : fUnite.getText().trim());
        np.setDescription(fDesc.getText().trim());
        try { np.setSeuilAlerte(Integer.parseInt(fSeuil.getText().trim())); }
        catch (Exception ex) { np.setSeuilAlerte(0); }
        try { np.setStatut(Produit.Statut.valueOf(fStatut.getValue())); }
        catch (Exception ex) { np.setStatut(Produit.Statut.Actif); }

        runAsync(() -> {
            boolean creation = np.getId() == null;
            if (creation) np.setId(produitDAO.save(np));
            else produitDAO.update(np);
            auditDAO.log(new JournalAudit("Produit", np.getId(),
                creation ? JournalAudit.CREATE : JournalAudit.UPDATE,
                session.getUserId(), session.getLogin(),
                np.getCode() + " (" + np.getPrixUnitaire() + " FCFA)"));
            return true;
        }, ok -> { appliquerFiltres(); annulerForm(); });
    }

    private void supprimerProduit() {
        if (produitEnCours == null) return;
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Supprimer définitivement " + produitEnCours.getLibelle() + " ?",
            ButtonType.YES, ButtonType.NO);
        conf.setHeaderText(null);
        conf.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            Produit toDelete = produitEnCours;
            runAsync(() -> { produitDAO.delete(toDelete.getId()); return true; },
                ok -> { appliquerFiltres(); annulerForm(); });
        });
    }

    private void annulerForm() {
        produitEnCours = null; modeNouv = false;
        lblFormTitre.setText("Sélectionnez un produit");
        fCode.clear(); fLib.clear(); fPrix.setText("0"); fUnite.setText("Pièce");
        fSeuil.setText("0"); fDesc.clear();
        fLib.setStyle(""); fPrix.setStyle("");
        btnSave.setDisable(true); btnAnnuler.setDisable(true); btnSuppr.setDisable(true);
        table.getSelectionModel().clearSelection();
    }

    // ══════════════════════════════════════════════════════════════
    //  REFRESH / FILTRES
    // ══════════════════════════════════════════════════════════════
    @Override
    public void refresh() { appliquerFiltres(); }

    private void appliquerFiltres() {
        String txt   = searchField != null ? searchField.getText() : null;
        Famille fam  = cboFamille != null ? cboFamille.getValue() : null;
        String famId = (fam != null && !fam.getId().isEmpty()) ? fam.getId() : null;
        runAsync(() -> produitDAO.search(txt, famId, false), list -> {
            data.setAll(list);
            lblCount.setText(list.size() + " produit(s)");
        });
    }

    private void chargerFamilles(String selectionnerId) {
        runAsync(() -> produitDAO.findAllFamilles(), familles -> {
            Famille tout = new Famille("", "Toutes les familles");
            cboFamille.getItems().clear();
            cboFamille.getItems().add(tout);
            cboFamille.getItems().addAll(familles);
            if (selectionnerId != null) {
                familles.stream().filter(f -> selectionnerId.equals(f.getId()))
                    .findFirst().ifPresent(cboFamille::setValue);
            } else {
                cboFamille.setValue(tout);
            }
            // Mettre aussi à jour le combobox du formulaire si ouvert
            if (fFamille != null) fFamille.getItems().setAll(familles);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════
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
}
