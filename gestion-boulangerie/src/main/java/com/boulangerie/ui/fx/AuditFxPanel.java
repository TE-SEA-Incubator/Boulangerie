package com.boulangerie.ui.fx;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.model.JournalAudit;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

/**
 * Journal d'Audit — lecture seule, filtrable, exportable CSV.
 */
public class AuditFxPanel extends FxPanelBase {

    private final AuditDAO auditDAO = new AuditDAO();

    private TableView<JournalAudit>       table;
    private ObservableList<JournalAudit>  data = FXCollections.observableArrayList();
    private ComboBox<String>  cboEntite, cboAction;
    private TextField         txtDu, txtAu;
    private Label             lblCount;
    private int               currentPage = 0;
    private static final int  PAGE = 50;

    public AuditFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        // ── Filtres ───────────────────────────────────────────────
        cboEntite = new ComboBox<>(FXCollections.observableArrayList(
            "Toutes","Utilisateur","Produit","Client","Facture",
            "Versement","Cloture","FicheJournaliere","Avoir"));
        cboEntite.setValue("Toutes");

        cboAction = new ComboBox<>(FXCollections.observableArrayList(
            "Toutes","CREATE","UPDATE","DELETE","LOGIN","LOGOUT",
            "BLOCK","UNBLOCK","AVOIR","ECART","CLOTURE"));
        cboAction.setValue("Toutes");

        txtDu = new TextField(FormatUtil.date(LocalDate.now().minusDays(7)));
        txtDu.setPromptText("dd/MM/yyyy"); txtDu.setPrefWidth(110);
        txtAu = new TextField(FormatUtil.date(LocalDate.now()));
        txtAu.setPromptText("dd/MM/yyyy"); txtAu.setPrefWidth(110);

        Button btnSearch = btnPrimary("Rechercher", null);
        Button btnExport = btnOutline("Export CSV");
        Button btnPrev   = btnOutline("◀ Précédent");
        Button btnNext   = btnOutline("Suivant ▶");

        btnSearch.setOnAction(e -> { currentPage = 0; refresh(); });
        btnExport.setOnAction(e -> exporterCSV());
        btnPrev.setOnAction(e   -> { if (currentPage > 0) { currentPage--; refresh(); } });
        btnNext.setOnAction(e   -> { currentPage++; refresh(); });

        // Titre + filtres dans VBox séparés (éviter double-parent Node)
        Label lblTitre = sectionTitle("Journal d'Audit");
        lblTitre.setPadding(new Insets(0, 0, 8, 0));

        HBox filtresRow = new HBox(8,
            new Label("Entité :"), cboEntite,
            new Label("Action :"), cboAction,
            new Label("Du :"),     txtDu,
            new Label("Au :"),     txtAu,
            btnSearch, btnExport, btnPrev, btnNext
        );
        filtresRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // ── Table ─────────────────────────────────────────────────
        table = styledTable();
        table.setItems(data);

        TableColumn<JournalAudit,String> colDate = new TableColumn<>("Date / Heure");
        TableColumn<JournalAudit,String> colEnt  = new TableColumn<>("Entité");
        TableColumn<JournalAudit,String> colAct  = new TableColumn<>("Action");
        TableColumn<JournalAudit,String> colUser = new TableColumn<>("Utilisateur");
        TableColumn<JournalAudit,String> colId   = new TableColumn<>("ID Entité");
        TableColumn<JournalAudit,String> colDet  = new TableColumn<>("Détails");

        colDate.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.dateHeure(d.getValue().getDateAction())));
        colEnt .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEntite()));
        colAct .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAction()));
        colUser.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getLoginUtilisateur() != null ? d.getValue().getLoginUtilisateur() : "—"));
        colId  .setCellValueFactory(d -> {
            String id = d.getValue().getEntiteId();
            return new SimpleStringProperty(id != null && id.length() > 8
                ? id.substring(0, 8) + "…" : (id != null ? id : "—"));
        });
        colDet .setCellValueFactory(d -> {
            String det = d.getValue().getDetails();
            return new SimpleStringProperty(det != null && det.length() > 80
                ? det.substring(0, 80) + "…" : (det != null ? det : "—"));
        });

        // Colorier l'action
        colAct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                String color = switch(item) {
                    case "CREATE" -> "#0F9D58";
                    case "DELETE", "BLOCK", "ECART" -> "#D93025";
                    case "UPDATE" -> "#F29900";
                    case "LOGIN"  -> "#1A73E8";
                    case "UNBLOCK"-> "#9334E6";
                    default -> "#5F6368";
                };
                setStyle("-fx-text-fill:" + color + "; -fx-font-weight:bold;");
            }
        });

        table.getColumns().addAll(colDate, colEnt, colAct, colUser, colId, colDet);

        // ── Footer ────────────────────────────────────────────────
        lblCount = footerCount("0 entrées");

        // Note immuabilité
        Label lblNote = new Label(
            "⚠  Le journal d'audit est en lecture seule — aucune modification possible dans l'application.");
        lblNote.setStyle("-fx-font-size:11px; -fx-text-fill:#F29900; "
            + "-fx-background-color:#FEF7E0; -fx-background-radius:4; -fx-padding:4 8;");

        VBox body = new VBox(10, lblTitre, filtresRow, lblNote, table, lblCount);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        String entite = "Toutes".equals(cboEntite.getValue()) ? null : cboEntite.getValue();
        String action = "Toutes".equals(cboAction.getValue()) ? null : cboAction.getValue();
        LocalDate du  = FormatUtil.parseDate(txtDu.getText());
        LocalDate au  = FormatUtil.parseDate(txtAu.getText());

        runAsync(() -> {
            var list  = auditDAO.search(entite, action, null, du, au, PAGE, currentPage * PAGE);
            int total = auditDAO.count(entite, action, null, du, au);
            return new Object[]{list, total};
        }, res -> {
            @SuppressWarnings("unchecked")
            List<JournalAudit> list = (List<JournalAudit>) res[0];
            int total = (int) res[1];
            data.setAll(list);
            lblCount.setText("Page " + (currentPage + 1)
                + "  |  " + total + " entrées au total  |  " + PAGE + " / page");
        });
    }

    private void exporterCSV() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Exporter le journal d'audit");
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        fc.setInitialFileName("audit_" + LocalDate.now() + ".csv");
        File file = fc.showSaveDialog(mainWindow.getStage());
        if (file == null) return;

        runAsync(() -> {
            try (PrintWriter pw = new PrintWriter(file, "UTF-8")) {
                pw.println("Date/Heure;Entité;Action;Utilisateur;ID Entité;Détails");
                for (int r = 0; r < table.getItems().size(); r++) {
                    JournalAudit a = table.getItems().get(r);
                    pw.println(String.join(";",
                        clean(FormatUtil.dateHeure(a.getDateAction())),
                        clean(a.getEntite()),
                        clean(a.getAction()),
                        clean(a.getLoginUtilisateur()),
                        clean(a.getEntiteId()),
                        clean(a.getDetails())
                    ));
                }
            }
            return true;
        }, ok -> mainWindow.showAlert("Export",
            "CSV exporté : " + file.getName(), Alert.AlertType.INFORMATION));
    }

    private String clean(String s) {
        return s != null ? s.replace(";", ",") : "";
    }
}
