package com.boulangerie.ui.fx;

import com.boulangerie.dao.VersementDAO;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Module Recouvrement & Rapprochement de caisse — Planche 2 écran 12.
 */
public class RecouvrementFxPanel extends FxPanelBase {

    private final VersementDAO  versementDAO  = new VersementDAO();
    private final CaisseService caisseService = new CaisseService();
    private final SessionService session      = SessionService.getInstance();

    // Indicateurs
    private Label lblAttendu, lblRemis, lblEnregistre;
    private Label lblEcart, lblTaux;
    private Label lblSoldeCloture;
    private ComboBox<String> cboMotifEcart;
    private Button btnValider;

    public RecouvrementFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        VBox body = new VBox(16);
        body.setFillWidth(true);
        body.setPadding(new Insets(0, 0, 0, 0));

        // ── Titre ─────────────────────────────────────────────────
        body.getChildren().add(sectionTitle("Rapprochement & Clôture de caisse"));

        // ── Date indicative ───────────────────────────────────────
        Label lblDate = new Label("Journée du : " + FormatUtil.date(LocalDate.now()));
        lblDate.setStyle("-fx-font-size:13px; -fx-text-fill:#5F6368;");
        body.getChildren().add(lblDate);

        // ── 3 blocs montants ──────────────────────────────────────
        HBox montantsRow = new HBox(14);
        montantsRow.setFillHeight(true);

        lblAttendu    = new Label("—");
        lblRemis      = new Label("—");
        lblEnregistre = new Label("—");

        montantsRow.getChildren().addAll(
            montantCard("Montant attendu (TTC)",    lblAttendu,    "#5F6368"),
            montantCard("Montant remis",            lblRemis,      "#1A73E8"),
            montantCard("Montant enregistré (TTC)", lblEnregistre, "#0F9D58")
        );
        for (var n : montantsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        body.getChildren().add(montantsRow);

        // ── Bloc écart ────────────────────────────────────────────
        VBox ecartBox = new VBox(10);
        ecartBox.setStyle("-fx-background-color:#FCE8E6; -fx-background-radius:8; "
            + "-fx-border-color:#D93025; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16;");

        lblEcart = new Label("Écart constaté : —");
        lblEcart.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D93025;");

        Label lblMotifLbl = new Label("Motif de l'écart (obligatoire si écart ≠ 0) :");
        lblMotifLbl.setStyle("-fx-font-size:12px;");

        cboMotifEcart = new ComboBox<>();
        cboMotifEcart.getItems().addAll(
            "—",
            "Chèques en attente d'encaissement",
            "Erreur de comptage",
            "Dépôt partiel",
            "Billets endommagés",
            "Autre"
        );
        cboMotifEcart.setValue("—");
        cboMotifEcart.setMaxWidth(Double.MAX_VALUE);

        CheckBox chkVerif = new CheckBox("Chaque versement dispose d'un reçu associé ✓");
        chkVerif.setStyle("-fx-font-size:12px;");

        ecartBox.getChildren().addAll(lblEcart, lblMotifLbl, cboMotifEcart, chkVerif);
        body.getChildren().add(ecartBox);

        // ── Résultat recouvrement ─────────────────────────────────
        GridPane recGrid = new GridPane();
        recGrid.setHgap(24); recGrid.setVgap(8);
        recGrid.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16;");

        Label lblRecTitle = new Label("Résultat du recouvrement");
        lblRecTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold;");
        recGrid.add(lblRecTitle, 0, 0, 3, 1);

        Label lblObjTitle = new Label("Objectif (TTC)");
        lblObjTitle.setStyle("-fx-text-fill:#5F6368; -fx-font-size:11px;");
        Label lblRealTitle = new Label("Réalisé (TTC)");
        lblRealTitle.setStyle("-fx-text-fill:#5F6368; -fx-font-size:11px;");
        Label lblTauxTitle = new Label("Taux de recouvrement");
        lblTauxTitle.setStyle("-fx-text-fill:#5F6368; -fx-font-size:11px;");
        recGrid.addRow(1, lblObjTitle, lblRealTitle, lblTauxTitle);

        Label lblObj  = new Label("—"); lblObj.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");
        Label lblReal = new Label("—"); lblReal.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");
        lblTaux = new Label("—");
        lblTaux.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:#0F9D58;");
        recGrid.addRow(2, lblObj, lblReal, lblTaux);

        body.getChildren().add(recGrid);

        // ── Solde de clôture ──────────────────────────────────────
        HBox soldeBox = new HBox(12);
        soldeBox.setAlignment(Pos.CENTER_LEFT);
        soldeBox.setStyle("-fx-background-color:#E8F0FE; -fx-background-radius:8; "
            + "-fx-padding:12 16;");
        Label lblSoldeTitle = new Label("Solde de clôture J  =  Solde d'ouverture J+1 :");
        lblSoldeTitle.setStyle("-fx-font-size:13px;");
        lblSoldeCloture = new Label("—");
        lblSoldeCloture.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#D93025;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        soldeBox.getChildren().addAll(lblSoldeTitle, spacer, lblSoldeCloture);
        body.getChildren().add(soldeBox);

        // ── Bouton valider ────────────────────────────────────────
        btnValider = btnSuccess("🔒  Valider et clôturer la caisse", null);
        btnValider.setMaxWidth(Double.MAX_VALUE);
        btnValider.setStyle(btnValider.getStyle()
            + "-fx-font-size:14px; -fx-pref-height:44; -fx-font-weight:bold;");
        btnValider.setOnAction(e -> cloturerJour());

        HBox btnRow = new HBox(btnValider);
        HBox.setHgrow(btnValider, Priority.ALWAYS);
        body.getChildren().add(btnRow);

        // Stocker refs pour refresh
        // (lblObj et lblReal sont locales — on les recharge dans refresh)
        root.setCenter(scrollPane(body));
    }

    private VBox montantCard(String titre, Label lblVal, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color:white; -fx-background-radius:8; "
            + "-fx-border-color:#DADCE0; -fx-border-radius:8; -fx-border-width:1; "
            + "-fx-padding:14 16; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,1);");
        Label t = new Label(titre);
        t.setStyle("-fx-font-size:11px; -fx-text-fill:#5F6368;");
        lblVal.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:" + color + ";");
        card.getChildren().addAll(t, lblVal);
        return card;
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        runAsync(() -> new BigDecimal[]{
            versementDAO.getMontantAttenduJour(today),
            versementDAO.getMontantRemisJour(today),
            versementDAO.getMontantEnregistreJour(today)
        }, arr -> {
            BigDecimal attendu    = arr[0];
            BigDecimal remis      = arr[1];
            BigDecimal enregistre = arr[2];
            BigDecimal ecart      = remis.subtract(enregistre);
            BigDecimal taux = attendu.compareTo(BigDecimal.ZERO) > 0
                ? enregistre.divide(attendu, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            lblAttendu.setText(FormatUtil.montant(attendu) + " FCFA");
            lblRemis.setText(FormatUtil.montant(remis) + " FCFA");
            lblEnregistre.setText(FormatUtil.montant(enregistre) + " FCFA");

            lblEcart.setText("Écart constaté : " + FormatUtil.montant(ecart) + " FCFA");
            lblEcart.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:"
                + (ecart.compareTo(BigDecimal.ZERO) != 0 ? "#D93025" : "#0F9D58") + ";");

            lblTaux.setText(taux + " %");
            lblTaux.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:"
                + (taux.compareTo(BigDecimal.valueOf(80)) >= 0 ? "#0F9D58" : "#D93025") + ";");

            lblSoldeCloture.setText(FormatUtil.montant(ecart) + " FCFA");
        });
    }

    private void cloturerJour() {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            mainWindow.showAlert("Accès", "Permission refusée.", Alert.AlertType.ERROR); return;
        }
        String motif = cboMotifEcart.getValue();
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
            "Valider la clôture de caisse pour le " + FormatUtil.date(LocalDate.now()) + " ?");
        conf.setTitle("Clôture"); conf.setHeaderText(null);
        conf.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            runAsync(() -> {
                caisseService.cloturerJour(LocalDate.now(),
                    "—".equals(motif) ? null : motif);
                return true;
            }, ok -> {
                btnValider.setDisable(true);
                btnValider.setText("✓  Clôture validée");
                refresh();
                mainWindow.showAlert("Clôture", "Caisse clôturée avec succès.", Alert.AlertType.INFORMATION);
            });
        });
    }
}
