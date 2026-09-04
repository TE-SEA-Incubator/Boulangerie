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
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ProduitsFxPanel extends FxPanelBase {

    private final ProduitDAO produitDAO = new ProduitDAO();
    private final AuditDAO   auditDAO   = new AuditDAO();
    private final SessionService session = SessionService.getInstance();

    private TableView<Produit>         table;
    private ObservableList<Produit>    data = FXCollections.observableArrayList();
    private TextField                  searchField;
    private ComboBox<Famille>          cboFamille;
    private Label                      lblCount;

    public ProduitsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        // ── Toolbar ───────────────────────────────────────────────
        searchField = searchField("Rechercher un produit…");
        searchField.textProperty().addListener((o, ov, nv) -> appliquerFiltres());

        cboFamille = new ComboBox<>();
        cboFamille.setPromptText("Toutes les familles");
        cboFamille.setStyle("-fx-pref-width: 160;");
        cboFamille.setOnAction(e -> appliquerFiltres());

        Button btnNouv  = btnPrimary("+ Nouveau produit", BootstrapIcons.PLUS_CIRCLE);
        Button btnEdit  = btnOutline("Modifier");
        Button btnTarif = btnOutline("Gérer les tarifs");
        Button btnPDF   = btnOutline("Export PDF");

        btnNouv.setOnAction(e -> ouvrirFormulaire(null));
        btnEdit.setOnAction(e -> ouvrirFormulaireSelection());
        btnTarif.setOnAction(e -> gererTarifs());
        btnPDF.setOnAction(e  -> exporterPDF());

        HBox toolbar = toolbar(
            btnNouv, btnEdit, btnTarif,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            searchField, cboFamille, btnPDF);

        // ── Table ─────────────────────────────────────────────────
        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if (e.getClickCount()==2) ouvrirFormulaireSelection(); });

        TableColumn<Produit, String> colCode     = new TableColumn<>("Code");
        TableColumn<Produit, String> colLib      = new TableColumn<>("Libellé");
        TableColumn<Produit, String> colFam      = new TableColumn<>("Famille");
        TableColumn<Produit, String> colUnite    = new TableColumn<>("Unité");
        TableColumn<Produit, String> colStatut   = new TableColumn<>("Statut");
        TableColumn<Produit, String> colSeuil    = new TableColumn<>("Seuil alerte");

        colCode .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCode()));
        colLib  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLibelle()));
        colFam  .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getFamille() != null ? d.getValue().getFamille().getNom() : "—"));
        colUnite.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUnite()));
        colStatut.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getStatut() != null ? d.getValue().getStatut().name() : "—"));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });
        colSeuil.setCellValueFactory(d -> new SimpleStringProperty(
            String.valueOf(d.getValue().getSeuilAlerte())));

        table.getColumns().addAll(colCode, colLib, colFam, colUnite, colStatut, colSeuil);

        // ── Footer ────────────────────────────────────────────────
        lblCount = footerCount("0 produits");

        // ── Assemblage ────────────────────────────────────────────
        VBox body = new VBox(10);
        body.getChildren().addAll(
            header("Catalogue Produits & Tarifs", toolbar.getChildren().toArray(new javafx.scene.Node[0])),
            table, lblCount
        );
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);

        // Charger familles
        runAsync(() -> produitDAO.findAllFamilles(), familles -> {
            Famille tout = new Famille("", "Toutes");
            cboFamille.getItems().clear();
            cboFamille.getItems().add(tout);
            cboFamille.getItems().addAll(familles);
            cboFamille.setValue(tout);
        });
    }

    @Override
    public void refresh() { appliquerFiltres(); }

    private void appliquerFiltres() {
        String txt  = searchField.getText();
        Famille fam = cboFamille.getValue();
        String famId = (fam != null && !fam.getId().isEmpty()) ? fam.getId() : null;

        runAsync(() -> produitDAO.search(txt, famId, false), list -> {
            data.setAll(list);
            lblCount.setText(list.size() + " produit(s)");
        });
    }

    private void ouvrirFormulaire(Produit p) {
        // Dialog de création/modification (simple pour cette version)
        Dialog<Produit> dlg = new Dialog<>();
        dlg.setTitle(p == null ? "Nouveau produit" : "Modifier — " + p.getCode());
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(520);
        dlg.initOwner(mainWindow.getStage());

        GridPane form = new GridPane();
        form.setHgap(14); form.setVgap(10);
        form.setPadding(new Insets(20));
        // Colonnes bien dimensionnées pour éviter la troncature des labels
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(150); c0.setPrefWidth(170); c0.setHgrow(Priority.NEVER);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS); c1.setFillWidth(true); c1.setMinWidth(220);
        form.getColumnConstraints().addAll(c0, c1);

        TextField txtCode    = new TextField(p != null ? p.getCode()    : "");
        TextField txtLib     = new TextField(p != null ? p.getLibelle() : "");
        TextField txtUnite   = new TextField(p != null ? p.getUnite()   : "Pièce");
        TextField txtSeuil   = new TextField(p != null ? String.valueOf(p.getSeuilAlerte()) : "0");
        TextArea txtDescription = new TextArea(p != null && p.getDescription() != null ? p.getDescription() : "");
        txtDescription.setPrefRowCount(3);
        ComboBox<Famille> cboFamilleForm = new ComboBox<>(FXCollections.observableArrayList(cboFamille.getItems()));
        cboFamilleForm.getItems().removeIf(f -> f.getId() == null || f.getId().isBlank());
        cboFamilleForm.setValue(p != null ? p.getFamille() : null);
        ComboBox<String> cboStatut = new ComboBox<>(
            FXCollections.observableArrayList("Actif", "Inactif"));
        cboStatut.setValue(p != null && p.getStatut() != null ? p.getStatut().name() : "Actif");

        form.addRow(0, new Label("Code *"),    txtCode);
        form.addRow(1, new Label("Libellé *"), txtLib);
        form.addRow(2, new Label("Famille *"), cboFamilleForm);
        form.addRow(3, new Label("Unité *"),   txtUnite);
        form.addRow(4, new Label("Seuil alerte"), txtSeuil);
        form.addRow(5, new Label("Statut"),    cboStatut);
        form.addRow(6, new Label("Description"), txtDescription);

        dlg.getDialogPane().setContent(form);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Produit np = p != null ? p : new Produit();
                np.setCode(txtCode.getText().trim());
                np.setLibelle(txtLib.getText().trim());
                np.setFamille(cboFamilleForm.getValue());
                np.setUnite(txtUnite.getText().trim());
                np.setDescription(txtDescription.getText().trim());
                try { np.setSeuilAlerte(Integer.parseInt(txtSeuil.getText().trim())); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Le seuil doit être un nombre entier."); }
                np.setStatut(Produit.Statut.valueOf(cboStatut.getValue()));
                return np;
            }
            return null;
        });

        dlg.showAndWait().ifPresent(np -> {
            if (np.getCode().isBlank() || np.getLibelle().isBlank() || np.getUnite().isBlank()
                    || np.getFamille() == null || np.getSeuilAlerte() < 0) {
                mainWindow.showAlert("Validation", "Code, libellé, famille, unité et seuil valide sont obligatoires.", Alert.AlertType.WARNING);
                return;
            }
            runAsync(() -> {
                boolean creation = np.getId() == null;
                if (creation) np.setId(produitDAO.save(np));
                else produitDAO.update(np);
                auditDAO.log(new JournalAudit("Produit", np.getId(),
                    creation ? JournalAudit.CREATE : JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(), "Produit: " + np.getCode()));
                return true;
            }, ok -> refresh());
        });
    }

    private void ouvrirFormulaireSelection() {
        Produit sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) ouvrirFormulaire(sel);
    }

    private void exporterPDF() {
        mainWindow.navigate(MainWindow.RAPPORTS);
    }

    private void gererTarifs() {
        Produit selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            mainWindow.showAlert("Tarifs", "Sélectionnez un produit.", Alert.AlertType.INFORMATION);
            return;
        }
        runAsync(() -> produitDAO.findById(selected.getId()).orElseThrow(), this::ouvrirDialogTarifs);
    }

    private void ouvrirDialogTarifs(Produit produit) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Tarifs - " + produit.getLibelle());
        dlg.setHeaderText("Les tarifs sont datés : aucune facture déjà émise ne sera modifiée.");
        dlg.getDialogPane().setPrefWidth(680);

        TableView<Tarif> tarifs = styledTable();
        tarifs.setPrefHeight(220);
        TableColumn<Tarif, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTypeTarif().name()));
        TableColumn<Tarif, String> montant = new TableColumn<>("Montant FCFA");
        montant.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontant())));
        TableColumn<Tarif, String> debut = new TableColumn<>("Début");
        debut.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateDebut())));
        TableColumn<Tarif, String> fin = new TableColumn<>("Fin");
        fin.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateFin())));
        tarifs.getColumns().addAll(type, montant, debut, fin);
        tarifs.getItems().setAll(produit.getTarifs());

        ComboBox<Tarif.TypeTarif> cboType = new ComboBox<>(FXCollections.observableArrayList(Tarif.TypeTarif.values()));
        cboType.setValue(Tarif.TypeTarif.Standard);
        TextField txtMontant = new TextField();
        DatePicker dpDebut = new DatePicker(LocalDate.now());
        DatePicker dpFin = new DatePicker();
        Button ajouter = btnPrimary("Ajouter le tarif", BootstrapIcons.PLUS_CIRCLE);
        ajouter.setOnAction(e -> {
            try {
                BigDecimal valeur = new BigDecimal(txtMontant.getText().trim().replace(',', '.'));
                if (valeur.signum() < 0) throw new NumberFormatException();
                Tarif tarif = new Tarif();
                tarif.setProduitId(produit.getId()); tarif.setTypeTarif(cboType.getValue());
                tarif.setMontant(valeur); tarif.setDateDebut(dpDebut.getValue()); tarif.setDateFin(dpFin.getValue());
                tarif.setStatut(Tarif.Statut.Actif);
                runAsync(() -> { produitDAO.saveTarif(tarif); return produitDAO.findTarifs(produit.getId()); }, list -> {
                    tarifs.getItems().setAll(list); txtMontant.clear(); dpFin.setValue(null);
                });
            } catch (Exception ex) {
                mainWindow.showAlert("Tarif", "Saisissez un montant positif et une date de début.", Alert.AlertType.WARNING);
            }
        });
        GridPane form = new GridPane(); form.setHgap(8); form.setVgap(8);
        form.addRow(0, new Label("Type"), cboType, new Label("Montant"), txtMontant);
        form.addRow(1, new Label("Début"), dpDebut, new Label("Fin"), dpFin);
        VBox box = new VBox(12, tarifs, new Label("Nouveau tarif"), form, ajouter);
        box.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(box); dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }
}
