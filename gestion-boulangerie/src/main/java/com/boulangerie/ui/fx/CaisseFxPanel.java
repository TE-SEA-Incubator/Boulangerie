package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CaisseFxPanel extends FxPanelBase {

    private final VersementDAO  versementDAO = new VersementDAO();
    private final FactureDAO    factureDAO   = new FactureDAO();
    private final CaisseService caisseService = new CaisseService();
    private final SessionService session     = SessionService.getInstance();

    private TableView<Versement>       table;
    private ObservableList<Versement>  data = FXCollections.observableArrayList();
    private Label lblAttendu, lblRemis, lblEcart;

    public CaisseFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        Button btnNouv  = btnPrimary("+ Nouveau versement", BootstrapIcons.CASH);
        Button btnRefr  = btnOutline("⟳ Actualiser");
        Button btnClot  = btnDanger("🔒 Clôturer la caisse", null);

        btnNouv.setOnAction(e -> ouvrirVersement());
        btnRefr.setOnAction(e -> refresh());
        btnClot.setOnAction(e -> cloturerCaisse());

        table = styledTable();
        table.setItems(data);

        TableColumn<Versement,String> colNum  = new TableColumn<>("N° Reçu");
        TableColumn<Versement,String> colLiv  = new TableColumn<>("Livreur");
        TableColumn<Versement,String> colFac  = new TableColumn<>("Facture");
        TableColumn<Versement,String> colAtt  = new TableColumn<>("Attendu (TTC)");
        TableColumn<Versement,String> colRem  = new TableColumn<>("Remis");
        TableColumn<Versement,String> colEnr  = new TableColumn<>("Enregistré");
        TableColumn<Versement,String> colEcar = new TableColumn<>("Écart");
        TableColumn<Versement,String> colStat = new TableColumn<>("Statut");

        colNum .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colLiv .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLivreur()!=null?d.getValue().getLivreur().getNomComplet():"—"));
        colFac .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFacture()!=null?d.getValue().getFacture().getNumero():"—"));
        colAtt .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantAttendu())));
        colRem .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantRemis())));
        colEnr .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantEnregistre())));
        colEcar.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getEcart())));
        colStat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatut().name()));
        colStat.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty?null:badge(item));
            }
        });
        // Écart coloré
        colEcar.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(" ","").replace(",","."));
                    setStyle(v != 0 ? "-fx-text-fill:#D93025; -fx-font-weight:bold;" : "-fx-text-fill:#0F9D58;");
                } catch (Exception ignored) {}
            }
        });

        table.getColumns().addAll(colNum, colLiv, colFac, colAtt, colRem, colEnr, colEcar, colStat);

        // KPI bas
        lblAttendu = new Label("Attendu : —");
        lblRemis   = new Label("Remis : —");
        lblEcart   = new Label("Écart : —");
        for (Label l : new Label[]{lblAttendu, lblRemis, lblEcart}) {
            l.setStyle("-fx-font-size:13px; -fx-font-weight:bold; "
                + "-fx-background-color:white; -fx-border-color:#DADCE0; "
                + "-fx-border-radius:6; -fx-background-radius:6; -fx-padding:8 14;");
        }
        HBox kpiBar = new HBox(12, lblAttendu, lblRemis, lblEcart);
        kpiBar.setPadding(new Insets(6, 0, 0, 0));

        VBox body = new VBox(10,
            header("Caisse — Versements & Reçus", btnNouv, btnRefr, btnClot),
            table, kpiBar);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        runAsync(() -> {
            var list = versementDAO.findByDate(today);
            var att = versementDAO.getMontantAttenduJour(today);
            var rem = versementDAO.getMontantRemisJour(today);
            var ecart = rem.subtract(versementDAO.getMontantEnregistreJour(today));
            return new Object[]{list, att, rem, ecart};
        }, res -> {
            @SuppressWarnings("unchecked")
            var list = (List<Versement>) res[0];
            data.setAll(list);
            lblAttendu.setText("Attendu : " + FormatUtil.montant((BigDecimal)res[1]) + " FCFA");
            lblRemis.setText("Remis : "     + FormatUtil.montant((BigDecimal)res[2]) + " FCFA");
            BigDecimal ec = (BigDecimal)res[3];
            lblEcart.setText("Écart : " + FormatUtil.montant(ec) + " FCFA");
            lblEcart.setStyle(lblEcart.getStyle() + (ec.compareTo(BigDecimal.ZERO)!=0
                ? " -fx-text-fill:#D93025;" : " -fx-text-fill:#0F9D58;"));
        });
    }

    private void ouvrirVersement() {
        Dialog<boolean[]> dlg = new Dialog<>();
        dlg.setTitle("Enregistrer un versement"); dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(480);

        // Charger factures non soldées
        List<Facture> factures = factureDAO.findByFilters(null,null,null,"EnAttente");
        factures.addAll(factureDAO.findByFilters(null,null,null,"Partielle"));
        List<Utilisateur> livreurs = new UtilisateurDAO().findLivreurs();

        ComboBox<Facture>    cboFact = new ComboBox<>(FXCollections.observableArrayList(factures));
        ComboBox<Utilisateur> cboLiv = new ComboBox<>(FXCollections.observableArrayList(livreurs));
        TextField txtRemis = new TextField("0");
        ComboBox<String> cboMode = new ComboBox<>(FXCollections.observableArrayList(
            "Espèces","Chèque","Virement","Mobile Money","Autre"));
        cboMode.setValue("Espèces");
        Label lblAttenduDlg = new Label("—");
        cboFact.setOnAction(e -> {
            Facture f = cboFact.getValue();
            if (f != null) lblAttenduDlg.setText(FormatUtil.montant(f.getMontantTtc()) + " FCFA");
        });

        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(14));
        form.addRow(0, new Label("Facture *"),       cboFact);
        form.addRow(1, new Label("Livreur"),          cboLiv);
        form.addRow(2, new Label("Montant attendu"), lblAttenduDlg);
        form.addRow(3, new Label("Montant reçu *"),  txtRemis);
        form.addRow(4, new Label("Mode paiement"),   cboMode);

        dlg.getDialogPane().setContent(form);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == ButtonType.OK ? new boolean[]{true} : null);

        dlg.showAndWait().ifPresent(r -> {
            Facture f = cboFact.getValue();
            if (f == null) return;
            try {
                BigDecimal attendu = f.getMontantTtc();
                BigDecimal remis   = new BigDecimal(txtRemis.getText().replace(",","."));
                BigDecimal ecart   = remis.subtract(attendu);

                // Motif si écart
                String motif = null;
                if (ecart.compareTo(BigDecimal.ZERO) != 0) {
                    TextInputDialog dlgM = new TextInputDialog();
                    dlgM.setTitle("Écart"); dlgM.setHeaderText(null);
                    dlgM.setContentText("Écart : " + FormatUtil.montant(ecart) + " FCFA\nMotif obligatoire :");
                    var mo = dlgM.showAndWait();
                    if (mo.isEmpty() || mo.get().isBlank()) return;
                    motif = mo.get();
                }

                final String motifFinal = motif;
                runAsync(() -> {
                    Versement v = new Versement();
                    v.setNumero(versementDAO.genererNumero());
                    v.setFacture(f); v.setClient(f.getClient());
                    v.setLivreur(cboLiv.getValue());
                    v.setMontantAttendu(attendu);
                    v.setMontantRemis(remis);
                    v.setMontantEnregistre(remis);
                    v.setModePaiement(cboMode.getValue());
                    v.setMotifEcart(motifFinal);
                    v.setDateVersement(LocalDate.now());
                    v.setCaissier(session.getUtilisateur());
                    caisseService.enregistrerVersement(v);
                    return true;
                }, ok -> {
                    refresh();
                    mainWindow.showAlert("Succès","Versement enregistré. Reçu généré.",Alert.AlertType.INFORMATION);
                });
            } catch (NumberFormatException ex) {
                mainWindow.showAlert("Erreur","Montant invalide.",Alert.AlertType.ERROR);
            }
        });
    }

    private void cloturerCaisse() {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            mainWindow.showAlert("Accès","Permission refusée.",Alert.AlertType.ERROR); return;
        }
        runAsync(() -> versementDAO.getEcartsCaisseJour(LocalDate.now()), this::demanderCloture);
    }

    private void demanderCloture(BigDecimal ecart) {
        String motif = null;
        if (ecart.compareTo(BigDecimal.ZERO) != 0) {
            TextInputDialog motifDialog = new TextInputDialog();
            motifDialog.setTitle("Écart de caisse");
            motifDialog.setHeaderText("Écart constaté : " + FormatUtil.montant(ecart) + " FCFA");
            motifDialog.setContentText("Motif obligatoire :");
            var resultat = motifDialog.showAndWait();
            if (resultat.isEmpty() || resultat.get().isBlank()) return;
            motif = resultat.get().trim();
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Valider la clôture de caisse pour " + FormatUtil.date(LocalDate.now()) + " ?");
        conf.setTitle("Clôture"); conf.setHeaderText(null);
        final String motifFinal = motif;
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r ->
            runAsync(() -> {
                caisseService.cloturerJour(LocalDate.now(), motifFinal);
                return true;
            }, ok -> {
                refresh();
                mainWindow.showAlert("Clôture","Caisse clôturée avec succès.",Alert.AlertType.INFORMATION);
            }));
    }
}
