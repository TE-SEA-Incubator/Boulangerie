package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.DeblocageService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.math.BigDecimal;
import java.util.List;

public class ClientsFxPanel extends FxPanelBase {

    private final ClientDAO       clientDAO  = new ClientDAO();
    private final AuditDAO        auditDAO   = new AuditDAO();
    private final DeblocageService deblocage = new DeblocageService();
    private final SessionService  session    = SessionService.getInstance();

    private TableView<Client>        table;
    private ObservableList<Client>   data = FXCollections.observableArrayList();
    private TextField                searchField;
    private ComboBox<String>         cboStatut;
    private Label                    lblCount;

    public ClientsFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        searchField = searchField("Rechercher un client…");
        searchField.textProperty().addListener((o,ov,nv) -> refresh());

        cboStatut = new ComboBox<>(FXCollections.observableArrayList(
            "Tous", "Actif", "Bloqué", "Inactif"));
        cboStatut.setValue("Tous");
        cboStatut.setOnAction(e -> refresh());

        Button btnNouv    = btnPrimary("+ Nouveau client",  BootstrapIcons.PERSON_PLUS);
        Button btnBloquer = btnDanger("Bloquer",           BootstrapIcons.SLASH_CIRCLE);
        Button btnDebloq  = btnSuccess("Débloquer",        BootstrapIcons.CHECK_CIRCLE);
        Button btnPDF     = btnOutline("Export PDF");

        btnNouv.setOnAction(e   -> ouvrirFormulaire(null));
        btnBloquer.setOnAction(e -> bloquerSelection());
        btnDebloq.setOnAction(e  -> debloquerSelection());

        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if (e.getClickCount()==2) ouvrirFormulaire(table.getSelectionModel().getSelectedItem()); });

        // Colonnes
        TableColumn<Client,String> colCode  = col("Code",          c -> c.getCode());
        TableColumn<Client,String> colNom   = col("Nom / Raison",  c -> c.getNom());
        TableColumn<Client,String> colAdresse = col("Adresse",     c -> c.getAdresse() != null && !c.getAdresse().isBlank() ? c.getAdresse() : "—");
        TableColumn<Client,String> colTel   = col("Téléphone",     c -> c.getTelephone() != null ? c.getTelephone() : "—");
        TableColumn<Client,String> colCat   = col("Catégorie",     c -> c.getCategorie() != null ? c.getCategorie().getNom() : "—");
        TableColumn<Client,String> colType  = col("Type",          c -> c.getTypeClient().name());
        TableColumn<Client,String> colSolde = col("Solde (FCFA)",  c -> FormatUtil.montant(c.getSoldeActuel()));
        TableColumn<Client,String> colStat  = col("Statut",        c -> c.getStatut().name());

        // Badge statut
        colStat.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : badge(item));
            }
        });
        // Solde coloré
        colSolde.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(" ","").replace(",","."));
                    setStyle(v > 0 ? "-fx-text-fill:#D93025; -fx-alignment:CENTER-RIGHT; -fx-font-weight:bold;"
                                   : "-fx-text-fill:#0F9D58; -fx-alignment:CENTER-RIGHT; -fx-font-weight:bold;");
                } catch (Exception ex) { setStyle(""); }
            }
        });

        table.getColumns().addAll(colCode, colNom, colAdresse, colTel,
            colCat, colType, colSolde, colStat);

        lblCount = footerCount("0 partenaires (clients / livreurs)");

        VBox body = new VBox(10);
        body.getChildren().addAll(
            header("Clients & Livreurs",
                btnNouv, btnBloquer, btnDebloq,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                searchField, cboStatut, btnPDF),
            table, lblCount);
        VBox.setVgrow(table, Priority.ALWAYS);
        body.setFillWidth(true);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        String txt = searchField.getText();
        String stat = "Tous".equals(cboStatut.getValue()) ? null : cboStatut.getValue();
        runAsync(() -> clientDAO.search(txt, null, stat, false), list -> {
            data.setAll(list);
            lblCount.setText(list.size() + " client(s)");
        });
    }

    private <T> TableColumn<Client, String> col(String titre,
            java.util.function.Function<Client, String> fn) {
        TableColumn<Client, String> c = new TableColumn<>(titre);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return c;
    }

    private void ouvrirFormulaire(Client cl) {
        final boolean isNouveau = (cl == null);
        Dialog<Client> dlg = new Dialog<>();
        dlg.setTitle(isNouveau ? "Nouveau Partenaire (Client / Livreur)" : "Fiche Partenaire — " + cl.getNom());
        dlg.setHeaderText(isNouveau ? "Création d'un client ou livreur (code auto-généré)" : "Modification du partenaire");
        dlg.getDialogPane().setPrefWidth(520);

        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(16));

        TextField txtCode = new TextField();
        txtCode.setEditable(false);
        txtCode.setStyle("-fx-background-color: #F1F3F4; -fx-text-fill: #3C4043; -fx-font-weight: bold;");
        txtCode.setText(isNouveau ? clientDAO.genererCode() : cl.getCode());

        TextField txtNom     = new TextField(cl != null ? cl.getNom() : "");
        TextField txtAdresse = new TextField(cl != null && cl.getAdresse() != null ? cl.getAdresse() : "");
        TextField txtQuartier= new TextField(cl != null && cl.getQuartier() != null ? cl.getQuartier() : "");
        TextField txtTel     = new TextField(cl != null && cl.getTelephone() != null ? cl.getTelephone() : "");
        TextField txtEmail   = new TextField(cl != null && cl.getEmail() != null ? cl.getEmail() : "");

        List<CategorieClient> cats = clientDAO.findAllCategories();
        ComboBox<CategorieClient> cboCat = new ComboBox<>(FXCollections.observableArrayList(cats));
        if (cl != null && cl.getCategorie() != null) {
            cats.stream().filter(c -> c.getId().equals(cl.getCategorie().getId()))
                .findFirst().ifPresent(cboCat::setValue);
        } else if (!cats.isEmpty()) cboCat.setValue(cats.get(0));

        ComboBox<String> cboType = new ComboBox<>(
            FXCollections.observableArrayList("Nominatif", "Anonyme"));
        cboType.setValue(cl != null ? cl.getTypeClient().name() : "Nominatif");

        form.addRow(0, new Label("Code *"),         txtCode);
        form.addRow(1, new Label("Nom / Raison *"), txtNom);
        form.addRow(2, new Label("Adresse *"),      txtAdresse);
        form.addRow(3, new Label("Quartier"),       txtQuartier);
        form.addRow(4, new Label("Téléphone"),      txtTel);
        form.addRow(5, new Label("Email"),          txtEmail);
        form.addRow(6, new Label("Catégorie"),      cboCat);
        form.addRow(7, new Label("Type"),           cboType);

        dlg.getDialogPane().setContent(form);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Client nc = cl != null ? cl : new Client();
            nc.setCode(txtCode.getText().trim());
            nc.setNom(txtNom.getText().trim());
            nc.setAdresse(txtAdresse.getText().trim());
            nc.setQuartier(txtQuartier.getText().trim());
            nc.setTelephone(txtTel.getText().trim());
            nc.setEmail(txtEmail.getText().trim());
            nc.setCategorie(cboCat.getValue());
            nc.setTypeClient(Client.TypeClient.valueOf(cboType.getValue()));
            nc.setEstAnonyme(Client.TypeClient.Anonyme.equals(nc.getTypeClient()));
            return nc;
        });

        dlg.showAndWait().ifPresent(nc -> {
            if (nc.getCode().isBlank() || nc.getNom().isBlank()) {
                mainWindow.showAlert("Validation", "Code et Nom obligatoires.", Alert.AlertType.WARNING); return;
            }
            runAsync(() -> {
                if (nc.getId() == null) clientDAO.save(nc);
                else clientDAO.update(nc);
                auditDAO.log(new JournalAudit("Client", nc.getId(),
                    nc.getId()==null ? JournalAudit.CREATE : JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(), "Partenaire: " + nc.getCode()));
                return true;
            }, ok -> refresh());
        });
    }

    private void bloquerSelection() {
        Client sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { mainWindow.showAlert("Info","Sélectionnez un client.",Alert.AlertType.INFORMATION); return; }
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Bloquer client"); dlg.setHeaderText(null);
        dlg.setContentText("Motif du blocage :");
        dlg.showAndWait().ifPresent(motif -> {
            if (!motif.isBlank()) {
                runAsync(() -> {
                    deblocage.bloquerClient(sel.getId(), motif, sel.getSoldeActuel());
                    return true;
                }, ok -> refresh());
            }
        });
    }

    private void debloquerSelection() {
        Client sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { mainWindow.showAlert("Info","Sélectionnez un client.",Alert.AlertType.INFORMATION); return; }
        if (!Client.Statut.Bloqué.equals(sel.getStatut())) {
            mainWindow.showAlert("Info","Ce client n'est pas bloqué.",Alert.AlertType.INFORMATION); return;
        }
        // Utiliser un dialogue simplifié pour le déblocage exceptionnel
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Déblocage — " + sel.getNom()); dlg.setHeaderText(null);
        dlg.setContentText("Motif du déblocage :");
        dlg.showAndWait().ifPresent(motif -> {
            if (!motif.isBlank()) {
                runAsync(() -> {
                    deblocage.debloquerExceptionnel(sel.getId(), motif, "", null, null);
                    return true;
                }, ok -> refresh());
            }
        });
    }
}
