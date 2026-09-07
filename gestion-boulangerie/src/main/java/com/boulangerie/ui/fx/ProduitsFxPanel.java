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
        Button btnPDF   = btnOutline("Export PDF");

        btnNouv.setOnAction(e -> ouvrirFormulaire(null));
        btnEdit.setOnAction(e -> ouvrirFormulaireSelection());
        btnPDF.setOnAction(e  -> exporterPDF());

        HBox toolbar = toolbar(
            btnNouv, btnEdit,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            searchField, cboFamille, btnPDF);

        // ── Table ─────────────────────────────────────────────────
        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if (e.getClickCount()==2) ouvrirFormulaireSelection(); });

        TableColumn<Produit, String> colCode     = new TableColumn<>("Code");
        TableColumn<Produit, String> colLib      = new TableColumn<>("Libellé");
        TableColumn<Produit, String> colFam      = new TableColumn<>("Famille");
        TableColumn<Produit, String> colPrix     = new TableColumn<>("Prix unitaire (FCFA)");
        TableColumn<Produit, String> colUnite    = new TableColumn<>("Unité");
        TableColumn<Produit, String> colStatut   = new TableColumn<>("Statut");
        TableColumn<Produit, String> colSeuil    = new TableColumn<>("Seuil alerte");

        colCode .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCode()));
        colLib  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLibelle()));
        colFam  .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getFamille() != null ? d.getValue().getFamille().getNom() : "—"));
        colPrix .setCellValueFactory(d -> new SimpleStringProperty(
            FormatUtil.montant(d.getValue().getPrixUnitaire()) + " FCFA"));
        colPrix.setStyle("-fx-font-weight: bold; -fx-text-fill: #1F3A5F;");
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

        table.getColumns().addAll(colCode, colLib, colFam, colPrix, colUnite, colStatut, colSeuil);

        // ── Footer ────────────────────────────────────────────────
        lblCount = footerCount("0 produits");

        // ── Assemblage avec image pain ────────────────────────────
        var imgPain = loadImage("assets/Image 1 1.png", 42, 42);

        // Titre + image
        HBox titreRow = new HBox(10);
        titreRow.setAlignment(Pos.CENTER_LEFT);
        if (imgPain != null) titreRow.getChildren().add(imgPain);
        Label titreLabel = sectionTitle("Catalogue des Produits");
        titreRow.getChildren().add(titreLabel);

        Region accent = new Region();
        accent.setStyle("-fx-background-color:#F5A623; -fx-pref-height:3; -fx-max-height:3; -fx-background-radius:2; -fx-pref-width:44;");

        VBox hdr = new VBox(6, titreRow, accent, toolbar);
        hdr.setPadding(new Insets(0, 0, 8, 0));

        VBox body = new VBox(10, hdr, table, lblCount);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);

        // Charger familles
        chargerFamilles(null);
    }

    private void chargerFamilles(String selectionnerId) {
        runAsync(() -> produitDAO.findAllFamilles(), familles -> {
            Famille tout = new Famille("", "Toutes les familles");
            cboFamille.getItems().clear();
            cboFamille.getItems().add(tout);
            cboFamille.getItems().addAll(familles);
            if (selectionnerId != null) {
                familles.stream().filter(f -> selectionnerId.equals(f.getId())).findFirst().ifPresent(cboFamille::setValue);
            } else {
                cboFamille.setValue(tout);
            }
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
        final boolean isNouveau = (p == null);
        Dialog<Produit> dlg = new Dialog<>();
        dlg.setTitle(isNouveau ? "Nouveau produit" : "Modifier — " + p.getCode());
        dlg.setHeaderText(isNouveau ? "Saisie d'un nouveau produit (code généré automatiquement)" : "Modification du produit");
        dlg.getDialogPane().setPrefWidth(540);
        dlg.initOwner(mainWindow.getStage());

        GridPane form = new GridPane();
        form.setHgap(14); form.setVgap(10);
        form.setPadding(new Insets(20));
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(160); c0.setPrefWidth(170); c0.setHgrow(Priority.NEVER);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS); c1.setFillWidth(true); c1.setMinWidth(240);
        form.getColumnConstraints().addAll(c0, c1);

        // Code auto-généré
        TextField txtCode = new TextField();
        txtCode.setEditable(false);
        txtCode.setStyle("-fx-background-color: #F1F3F4; -fx-text-fill: #3C4043; -fx-font-weight: bold;");

        ComboBox<Famille> cboFamilleForm = new ComboBox<>();
        // Charger les familles actuelles
        List<Famille> famillesActuelles = produitDAO.findAllFamilles();
        cboFamilleForm.getItems().setAll(famillesActuelles);
        if (p != null && p.getFamille() != null) {
            famillesActuelles.stream().filter(f -> f.getId().equals(p.getFamille().getId())).findFirst()
                .ifPresent(cboFamilleForm::setValue);
        } else if (!famillesActuelles.isEmpty()) {
            cboFamilleForm.setValue(famillesActuelles.get(0));
        }

        // Si nouveau produit, générer le code selon la famille
        if (isNouveau) {
            String famId = cboFamilleForm.getValue() != null ? cboFamilleForm.getValue().getId() : null;
            txtCode.setText(produitDAO.genererCode(famId));
        } else {
            txtCode.setText(p.getCode());
        }

        // Bouton ajout nouvelle famille
        Button btnNouvelleFamille = new Button("+ Famille");
        btnNouvelleFamille.setStyle("-fx-background-color: #FFF3E0; -fx-text-fill: #1F3A5F; -fx-border-color: #F5A623; -fx-border-radius: 4; -fx-cursor: hand;");
        btnNouvelleFamille.setOnAction(ev -> {
            TextInputDialog famDlg = new TextInputDialog();
            famDlg.setTitle("Ajouter une famille");
            famDlg.setHeaderText("Création d'une nouvelle famille de produits");
            famDlg.setContentText("Nom de la nouvelle famille :");
            famDlg.showAndWait().ifPresent(nom -> {
                if (!nom.trim().isEmpty()) {
                    try {
                        Famille nf = produitDAO.saveFamille(nom.trim());
                        cboFamilleForm.getItems().add(nf);
                        cboFamilleForm.setValue(nf);
                        chargerFamilles(nf.getId());
                        if (isNouveau) {
                            txtCode.setText(produitDAO.genererCode(nf.getId()));
                        }
                    } catch (Exception ex) {
                        mainWindow.showAlert("Erreur", ex.getMessage(), Alert.AlertType.ERROR);
                    }
                }
            });
        });

        HBox familleBox = new HBox(8, cboFamilleForm, btnNouvelleFamille);
        HBox.setHgrow(cboFamilleForm, Priority.ALWAYS);
        cboFamilleForm.setMaxWidth(Double.MAX_VALUE);

        cboFamilleForm.setOnAction(ev -> {
            if (isNouveau && cboFamilleForm.getValue() != null) {
                txtCode.setText(produitDAO.genererCode(cboFamilleForm.getValue().getId()));
            }
        });

        TextField txtLib   = new TextField(p != null ? p.getLibelle() : "");
        TextField txtPrix  = new TextField(p != null && p.getPrixUnitaire() != null ? p.getPrixUnitaire().toPlainString() : "0");
        TextField txtUnite = new TextField(p != null ? p.getUnite() : "Pièce");
        TextField txtSeuil = new TextField(p != null ? String.valueOf(p.getSeuilAlerte()) : "0");
        TextArea txtDescription = new TextArea(p != null && p.getDescription() != null ? p.getDescription() : "");
        txtDescription.setPrefRowCount(3);

        ComboBox<String> cboStatut = new ComboBox<>(FXCollections.observableArrayList("Actif", "Inactif"));
        cboStatut.setValue(p != null && p.getStatut() != null ? p.getStatut().name() : "Actif");

        form.addRow(0, new Label("Code produit (auto)"), txtCode);
        form.addRow(1, new Label("Libellé du produit *"), txtLib);
        form.addRow(2, new Label("Famille *"),            familleBox);
        form.addRow(3, new Label("Prix unitaire (FCFA) *"), txtPrix);
        form.addRow(4, new Label("Unité de vente *"),     txtUnite);
        form.addRow(5, new Label("Seuil d'alerte"),       txtSeuil);
        form.addRow(6, new Label("Statut"),               cboStatut);
        form.addRow(7, new Label("Description"),          txtDescription);

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
                try {
                    BigDecimal prixVal = new BigDecimal(txtPrix.getText().trim().replace(',', '.'));
                    if (prixVal.signum() < 0) throw new NumberFormatException();
                    np.setPrixUnitaire(prixVal);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Le prix unitaire doit être un montant valide (≥ 0).");
                }
                try {
                    np.setSeuilAlerte(Integer.parseInt(txtSeuil.getText().trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Le seuil d'alerte doit être un nombre entier.");
                }
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
                    session.getUserId(), session.getLogin(), "Produit: " + np.getCode() + " (" + np.getPrixUnitaire() + " FCFA)"));
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
}
