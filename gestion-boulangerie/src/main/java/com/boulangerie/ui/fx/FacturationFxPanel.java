package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.FacturationService;
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

public class FacturationFxPanel extends FxPanelBase {

    private final FactureDAO          factureDAO  = new FactureDAO();
    private final FacturationService  factService = new FacturationService();
    private final SessionService      session     = SessionService.getInstance();

    private TableView<Facture>       table;
    private ObservableList<Facture>  data = FXCollections.observableArrayList();
    private Label                    lblFooter;

    public FacturationFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        Button btnRefresh = btnOutline("⟳ Actualiser");
        Button btnAvoir   = btnDanger("Créer un avoir", BootstrapIcons.FILE_MINUS);
        Button btnPDF     = btnOutline("Export PDF");

        btnRefresh.setOnAction(e -> refresh());
        btnAvoir.setOnAction(e   -> creerAvoir());

        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if(e.getClickCount()==2) aperçuFacture(); });

        TableColumn<Facture,String> colNum   = new TableColumn<>("N° Facture");
        TableColumn<Facture,String> colDate  = new TableColumn<>("Date");
        TableColumn<Facture,String> colClient= new TableColumn<>("Client");
        TableColumn<Facture,String> colLiv   = new TableColumn<>("Livreur");
        TableColumn<Facture,String> colHT    = new TableColumn<>("Montant HT");
        TableColumn<Facture,String> colTTC   = new TableColumn<>("TTC");
        TableColumn<Facture,String> colStat  = new TableColumn<>("Statut");
        TableColumn<Facture,String> colVerr  = new TableColumn<>("Verrou");

        colNum  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colDate .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateEmission())));
        colClient.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClient() != null ? d.getValue().getClient().getNom() : "Anonyme"));
        colLiv  .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getLivreur() != null ? d.getValue().getLivreur().getNomComplet() : "—"));
        colHT   .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantHt())));
        colTTC  .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getMontantTtc())));
        colStat .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatut().name()));
        colStat.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });
        colVerr.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().isEstVerrouillee() ? "🔒 Verrouillée" : "🔓 Ouverte"));

        table.getColumns().addAll(colNum, colDate, colClient, colLiv, colHT, colTTC, colStat, colVerr);

        // Info verrouillage
        Label lblVerrou = new Label("🔒  Les factures sont verrouillées dès leur émission — toute correction passe par un avoir traçable.");
        lblVerrou.setStyle("-fx-font-size:11px; -fx-text-fill:#D93025; "
            + "-fx-background-color:#FCE8E6; -fx-padding:6 10; -fx-background-radius:4;");

        lblFooter = footerCount("0 factures");

        VBox body = new VBox(10,
            header("Facturation", btnRefresh, btnAvoir, btnPDF),
            lblVerrou, table, lblFooter);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        runAsync(() -> factureDAO.findAll(), list -> {
            data.setAll(list);
            BigDecimal total = list.stream().filter(f -> !f.isEstAnnulee())
                .map(Facture::getMontantTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
            lblFooter.setText(list.size() + " facture(s)  |  Total TTC : "
                + FormatUtil.montant(total) + " FCFA");
        });
    }

    private void creerAvoir() {
        Facture sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { mainWindow.showAlert("Info","Sélectionnez une facture.",Alert.AlertType.INFORMATION); return; }
        if (!sel.isEstVerrouillee()) { mainWindow.showAlert("Info","Facture non verrouillée.",Alert.AlertType.WARNING); return; }
        if (!session.hasPermission("AVOIR_WRITE")) { mainWindow.showAlert("Accès","Permission refusée.",Alert.AlertType.ERROR); return; }

        Dialog<String[]> dlg = new Dialog<>();
        dlg.setTitle("Avoir sur " + sel.getNumero()); dlg.setHeaderText(null);
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(14));
        TextField txtMontant = new TextField();
        TextField txtMotif   = new TextField();
        form.addRow(0, new Label("Montant (FCFA) *"), txtMontant);
        form.addRow(1, new Label("Motif *"),           txtMotif);
        form.addRow(2, new Label("Facture : " + sel.getNumero() + "  — TTC : " + FormatUtil.montant(sel.getMontantTtc())));
        dlg.getDialogPane().setContent(form);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == ButtonType.OK
            ? new String[]{txtMontant.getText(), txtMotif.getText()} : null);

        dlg.showAndWait().ifPresent(r -> {
            try {
                BigDecimal montant = new BigDecimal(r[0].replace(",","."));
                if (r[1].isBlank()) { mainWindow.showAlert("Validation","Motif obligatoire.",Alert.AlertType.WARNING); return; }
                runAsync(() -> {
                    factService.creerAvoir(sel, montant, r[1]);
                    return true;
                }, ok -> {
                    refresh();
                    mainWindow.showAlert("Succès","Avoir créé et journalisé.",Alert.AlertType.INFORMATION);
                });
            } catch (NumberFormatException ex) {
                mainWindow.showAlert("Erreur","Montant invalide.",Alert.AlertType.ERROR);
            }
        });
    }

    private void aperçuFacture() {
        Facture sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Facture — " + sel.getNumero()); dlg.setHeaderText(null);
        dlg.setContentText(
            "N°       : " + sel.getNumero() + "\n"
            + "Date     : " + FormatUtil.date(sel.getDateEmission()) + "\n"
            + "Client   : " + (sel.getClient() != null ? sel.getClient().getNom() : "Anonyme") + "\n"
            + "Montant HT : " + FormatUtil.montant(sel.getMontantHt()) + " FCFA\n"
            + "TVA      : " + FormatUtil.montant(sel.getTvaMontant()) + " FCFA\n"
            + "TTC      : " + FormatUtil.montant(sel.getMontantTtc()) + " FCFA\n"
            + "Statut   : " + sel.getStatut().name() + "\n"
            + "Verrou   : " + (sel.isEstVerrouillee() ? "🔒 Oui" : "🔓 Non")
        );
        dlg.showAndWait();
    }
}
