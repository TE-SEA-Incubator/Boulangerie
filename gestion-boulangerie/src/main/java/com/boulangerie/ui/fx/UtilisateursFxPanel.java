package com.boulangerie.ui.fx;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.dao.UtilisateurDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.AuthService;
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

import java.util.List;

/**
 * Module Gestion Utilisateurs & Rôles — Planche 3.
 */
public class UtilisateursFxPanel extends FxPanelBase {

    private final UtilisateurDAO userDAO  = new UtilisateurDAO();
    private final AuditDAO       auditDAO = new AuditDAO();
    private final SessionService session  = SessionService.getInstance();

    private TableView<Utilisateur>       table;
    private ObservableList<Utilisateur>  data = FXCollections.observableArrayList();
    private Label                        lblCount;

    public UtilisateursFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        Button btnNouv  = btnPrimary("+ Nouvel utilisateur", BootstrapIcons.PERSON_PLUS);
        Button btnMdp   = btnOutline("Changer mot de passe");
        Button btnActif = btnDanger("Activer / Désactiver", null);
        Button btnRefr  = btnOutline("⟳ Actualiser");

        btnNouv.setOnAction(e  -> ouvrirFormulaire(null));
        btnMdp.setOnAction(e   -> changerMotDePasse());
        btnActif.setOnAction(e -> toggleActif());
        btnRefr.setOnAction(e  -> refresh());

        table = styledTable();
        table.setItems(data);
        table.setOnMouseClicked(e -> { if(e.getClickCount()==2) ouvrirFormulaire(table.getSelectionModel().getSelectedItem()); });

        TableColumn<Utilisateur,String> colLogin = new TableColumn<>("Login");
        TableColumn<Utilisateur,String> colNom   = new TableColumn<>("Nom complet");
        TableColumn<Utilisateur,String> colRole  = new TableColumn<>("Rôle");
        TableColumn<Utilisateur,String> colTel   = new TableColumn<>("Téléphone");
        TableColumn<Utilisateur,String> colEmail = new TableColumn<>("Email");
        TableColumn<Utilisateur,String> colConn  = new TableColumn<>("Dernière connexion");
        TableColumn<Utilisateur,String> colActif = new TableColumn<>("État");

        colLogin.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLogin()));
        colNom  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNomComplet()));
        colRole .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole()!=null?d.getValue().getRole().getNom():"—"));
        colTel  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTelephone()!=null?d.getValue().getTelephone():"—"));
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()!=null?d.getValue().getEmail():"—"));
        colConn .setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.dateHeure(d.getValue().getDerniereConnexion())));
        colActif.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActif()?"✓ Actif":"✗ Inactif"));
        colActif.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Label lbl = new Label(item);
                lbl.setStyle("-fx-font-weight:bold; -fx-text-fill:"
                    + (item.startsWith("✓") ? "#0F9D58" : "#D93025") + ";");
                setGraphic(lbl);
            }
        });

        table.getColumns().addAll(colLogin, colNom, colRole, colTel, colEmail, colConn, colActif);

        lblCount = footerCount("0 utilisateur(s)");

        VBox body = new VBox(10,
            header("Gestion Utilisateurs & Rôles", btnNouv, btnMdp, btnActif, btnRefr),
            table, lblCount);
        body.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(body);
    }

    @Override
    public void refresh() {
        runAsync(() -> userDAO.findAll(), list -> {
            data.setAll(list);
            lblCount.setText(list.size() + " utilisateur(s)");
        });
    }

    private void ouvrirFormulaire(Utilisateur u) {
        Dialog<Utilisateur> dlg = new Dialog<>();
        dlg.setTitle(u == null ? "Nouvel utilisateur" : "Modifier — " + u.getLogin());
        dlg.setHeaderText(null); dlg.getDialogPane().setPrefWidth(400);

        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(14));

        TextField txtLogin  = new TextField(u != null ? u.getLogin()       : "");
        TextField txtNom    = new TextField(u != null ? u.getNomComplet()   : "");
        TextField txtTel    = new TextField(u != null && u.getTelephone()!=null ? u.getTelephone() : "");
        TextField txtEmail  = new TextField(u != null && u.getEmail()!=null ? u.getEmail() : "");
        PasswordField txtMdp= new PasswordField();

        List<Role> roles = userDAO.findAllRoles();
        ComboBox<Role> cboRole = new ComboBox<>(FXCollections.observableArrayList(roles));
        if (u != null && u.getRole() != null) {
            roles.stream().filter(r -> r.getId().equals(u.getRole().getId()))
                .findFirst().ifPresent(cboRole::setValue);
        } else if (!roles.isEmpty()) cboRole.setValue(roles.get(0));

        form.addRow(0, new Label("Login *"),    txtLogin);
        form.addRow(1, new Label("Nom complet *"), txtNom);
        form.addRow(2, new Label("Téléphone"),  txtTel);
        form.addRow(3, new Label("Email"),      txtEmail);
        form.addRow(4, new Label("Rôle *"),     cboRole);
        if (u == null) form.addRow(5, new Label("Mot de passe *"), txtMdp);

        dlg.getDialogPane().setContent(form);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Utilisateur nu = u != null ? u : new Utilisateur();
            nu.setLogin(txtLogin.getText().trim());
            nu.setNomComplet(txtNom.getText().trim());
            nu.setTelephone(txtTel.getText().trim());
            nu.setEmail(txtEmail.getText().trim());
            nu.setRole(cboRole.getValue());
            nu.setActif(true);
            if (u == null && !txtMdp.getText().isBlank())
                nu.setMotDePasse(AuthService.hasher(txtMdp.getText()));
            return nu;
        });

        dlg.showAndWait().ifPresent(nu -> {
            if (nu.getLogin().isBlank() || nu.getNomComplet().isBlank()) {
                mainWindow.showAlert("Validation","Login et Nom obligatoires.",Alert.AlertType.WARNING); return;
            }
            runAsync(() -> {
                if (nu.getId() == null) { userDAO.save(nu); }
                else userDAO.update(nu);
                auditDAO.log(new JournalAudit("Utilisateur", nu.getId(),
                    nu.getId()==null ? JournalAudit.CREATE : JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(), "Utilisateur: " + nu.getLogin()));
                return true;
            }, ok -> refresh());
        });
    }

    private void changerMotDePasse() {
        Utilisateur sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { mainWindow.showAlert("Info","Sélectionnez un utilisateur.",Alert.AlertType.INFORMATION); return; }
        PasswordField pf = new PasswordField();
        pf.setPromptText("Nouveau mot de passe");
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Changer mot de passe — " + sel.getLogin());
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(pf);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == ButtonType.OK ? pf.getText() : null);
        dlg.showAndWait().ifPresent(mdp -> {
            if (!mdp.isBlank()) {
                runAsync(() -> {
                    userDAO.updatePassword(sel.getId(), AuthService.hasher(mdp));
                    auditDAO.log(new JournalAudit("Utilisateur", sel.getId(), JournalAudit.UPDATE,
                        session.getUserId(), session.getLogin(), "Mot de passe changé: " + sel.getLogin()));
                    return true;
                }, ok -> mainWindow.showAlert("Succès","Mot de passe mis à jour.",Alert.AlertType.INFORMATION));
            }
        });
    }

    private void toggleActif() {
        Utilisateur sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        runAsync(() -> {
            sel.setActif(!sel.isActif());
            userDAO.update(sel);
            auditDAO.log(new JournalAudit("Utilisateur", sel.getId(), JournalAudit.UPDATE,
                session.getUserId(), session.getLogin(),
                (sel.isActif()?"Activation":"Désactivation") + ": " + sel.getLogin()));
            return true;
        }, ok -> refresh());
    }
}
