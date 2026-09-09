package com.boulangerie.ui.fx;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainWindow;
import com.boulangerie.util.FormatUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import atlantafx.base.theme.Styles;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module CAISSE — Gestion financière journalière matricielle.
 * - Saisie des versements et des manquants cumulés.
 * - Suivi Visa (dernier caissier).
 * - Format: caisse jjmmaa.
 */
public class CaisseFxPanel extends FxPanelBase {

    private final CaisseService caisseService = new CaisseService();
    private final ClientDAO clientDAO = new ClientDAO();
    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final VersementDAO versementDAO = new VersementDAO();
    private final SessionService session = SessionService.getInstance();

    private StackPane rootView;
    private VBox vueListeFiches;
    private VBox vueDetailCaisse;
    private DatePicker dpDu, dpAu;
    private TableView<FicheJournaliere> tableListeFiches;
    private final ObservableList<FicheJournaliere> listeFichesObs = FXCollections.observableArrayList();

    private Label lblNumeroFiche;
    private Label lblSyncStatus;
    private Label lblTotalFactures, lblTotalVerses, lblTotalRestes;

    private TabPane tabsCategories;
    private List<CategorieClient> categoriesList = new ArrayList<>();
    private CategorieClient activeCategorie = null;

    private FicheJournaliere currentFiche;
    private List<Client> allClients = new ArrayList<>();
    private Map<String, BigDecimal> versementsVeille = new HashMap<>();
    private Map<String, FicheCaisseLigne> caisseLignes = new HashMap<>();

    private final ObservableList<CaisseRowData> dataMatrice = FXCollections.observableArrayList();
    private TableView<CaisseRowData> tableMatrice;
    private Label lblTotalsFooter;

    public CaisseFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        rootView = new StackPane();
        root.setCenter(rootView);
        buildListeView();
        buildDetailView();
        afficherVueListe();
    }

    private void buildListeView() {
        vueListeFiches = new VBox(10);
        vueListeFiches.setPadding(new Insets(14));
        vueListeFiches.getStyleClass().add("main-container");

        Label lblTitre = new Label("💰 CAISSE — Feuilles journalières");
        lblTitre.getStyleClass().add("section-title");

        HBox filtreBar = new HBox(10);
        filtreBar.setAlignment(Pos.CENTER_LEFT);
        filtreBar.getStyleClass().add("card");
        filtreBar.setPadding(new Insets(10));

        dpDu = new DatePicker(LocalDate.now().minusDays(30));
        dpAu = new DatePicker(LocalDate.now().plusDays(7));
        Button btnRech = btnPrimary("🔍 Appliquer", BootstrapIcons.FILTER);
        btnRech.setOnAction(e -> chargerListeFiches());

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnNouv = btnPrimary("➕ Nouvelle caisse", BootstrapIcons.PLUS_CIRCLE);
        btnNouv.setOnAction(e -> nouvelleCaisse());

        filtreBar.getChildren().addAll(new Label("Du :"), dpDu, new Label("Au :"), dpAu, btnRech, sp, btnNouv);

        tableListeFiches = new TableView<>();
        tableListeFiches.setItems(listeFichesObs);
        tableListeFiches.getStyleClass().addAll("styled-table", Styles.STRIPED);

        TableColumn<FicheJournaliere, String> colNum = new TableColumn<>("N° Feuille");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colNum.setPrefWidth(140);

        TableColumn<FicheJournaliere, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateFiche())));
        colDate.setPrefWidth(100);

        TableColumn<FicheJournaliere, Void> colAction = new TableColumn<>("Action");
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnOpen = btnPrimary("Ouvrir", BootstrapIcons.FOLDER2_OPEN);
            { btnOpen.setOnAction(e -> { FicheJournaliere f = getTableView().getItems().get(getIndex()); if (f != null) ouvrirCaisse(f); }); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null); else { HBox h = new HBox(btnOpen); h.setAlignment(Pos.CENTER); setGraphic(h); }
            }
        });

        tableListeFiches.getColumns().addAll(colNum, colDate, colAction);
        VBox.setVgrow(tableListeFiches, Priority.ALWAYS);

        vueListeFiches.getChildren().addAll(lblTitre, filtreBar, tableListeFiches);
    }

    private void buildDetailView() {
        vueDetailCaisse = new VBox(8);
        vueDetailCaisse.setPadding(new Insets(10));
        vueDetailCaisse.getStyleClass().add("main-container");

        Button btnRetour = btnOutline("← Retour à la liste");
        btnRetour.setOnAction(e -> afficherVueListe());

        lblNumeroFiche = new Label("N° —");
        lblNumeroFiche.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        lblSyncStatus = new Label("🟢 Prêt");
        lblSyncStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topBar = new HBox(12, btnRetour, lblNumeroFiche, sp, lblSyncStatus);
        topBar.setAlignment(Pos.CENTER_LEFT);

        lblTotalFactures = kpiCardLabel("Total Factures", "0 FCFA", "-app-bleu-nuit");
        lblTotalVerses   = kpiCardLabel("Total Versé", "0 FCFA", "-app-vert");
        lblTotalRestes   = kpiCardLabel("Total Reste", "0 FCFA", "-app-rouge");
        HBox kpiBox = new HBox(10, lblTotalFactures, lblTotalVerses, lblTotalRestes);

        tabsCategories = new TabPane();
        tabsCategories.getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);
        tabsCategories.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) { this.activeCategorie = (CategorieClient) nv.getUserData(); filtrerEtAfficherMatrice(); }
        });

        tableMatrice = new TableView<>();
        tableMatrice.getStyleClass().addAll("matrice-excel-table");
        tableMatrice.setItems(dataMatrice);
        tableMatrice.setEditable(true);

        lblTotalsFooter = new Label("TOTAL : 0 ligne(s)");
        lblTotalsFooter.getStyleClass().add("card");
        lblTotalsFooter.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10; -fx-background-color: #E2E8F0;");

        vueDetailCaisse.getChildren().addAll(topBar, kpiBox, tabsCategories, tableMatrice, lblTotalsFooter);
        VBox.setVgrow(tableMatrice, Priority.ALWAYS);
    }

    private Label kpiCardLabel(String titre, String valeur, String couleurVar) {
        Label lbl = new Label(titre + "\n" + valeur);
        lbl.getStyleClass().add("card");
        lbl.setStyle("-fx-padding: 10; -fx-font-weight: bold; -fx-text-fill: " + couleurVar + "; -fx-alignment: CENTER-LEFT;");
        lbl.setPrefWidth(200);
        return lbl;
    }

    private void chargerOuCreerCaisse(LocalDate date) {
        setSyncStatus("⏳ Chargement...", "#E37400");
        runAsync(() -> {
            FicheJournaliere f = ficheDAO.getOrCreateFicheJour(date, "CAISSE", session.getUserId());
            List<Client> clients = clientDAO.findAll();
            List<CategorieClient> cats = clientDAO.findAllCategories();
            List<FicheCaisseLigne> fcls = caisseService.chargerFicheCaisseJournaliere(date);
            Map<String, BigDecimal> vv = versementDAO.findTotalVersementsByDate(date.minusDays(1));
            return new Object[]{f, clients, cats, fcls, vv};
        }, res -> {
            this.currentFiche = (FicheJournaliere) res[0];
            this.allClients = (List<Client>) res[1];
            this.categoriesList = (List<CategorieClient>) res[2];
            @SuppressWarnings("unchecked") List<FicheCaisseLigne> fcls = (List<FicheCaisseLigne>) res[3];
            @SuppressWarnings("unchecked") Map<String, BigDecimal> vv = (Map<String, BigDecimal>) res[4];
            this.versementsVeille = vv;

            this.caisseLignes.clear();
            for (FicheCaisseLigne l : fcls) if (l.getClient() != null) caisseLignes.put(l.getClient().getId(), l);

            lblNumeroFiche.setText("Feuille CAISSE : " + currentFiche.getNumero());
            setSyncStatus("🟢 Prêt", "#2E7D32");
            mettreAJourOngletsCategories();
            filtrerEtAfficherMatrice();
        });
    }

    private void mettreAJourOngletsCategories() {
        tabsCategories.getTabs().clear();
        for (CategorieClient cat : categoriesList) {
            Tab t = new Tab(cat.getNom().toUpperCase());
            t.setUserData(cat); t.setClosable(false);
            tabsCategories.getTabs().add(t);
        }
        Tab all = new Tab("TOUS LES CLIENTS"); all.setUserData(null); all.setClosable(false);
        tabsCategories.getTabs().add(all);
        if (!tabsCategories.getTabs().isEmpty()) tabsCategories.getSelectionModel().select(0);
    }

    private void filtrerEtAfficherMatrice() {
        List<Client> filtres = allClients.stream()
            .filter(c -> activeCategorie == null || (c.getCategorie() != null && activeCategorie.getId().equals(c.getCategorie().getId())))
            .sorted(Comparator.comparing(c -> c.getNom() != null ? c.getNom() : ""))
            .collect(Collectors.toList());
        dataMatrice.clear();
        for (Client c : filtres) {
            FicheCaisseLigne fcl = caisseLignes.getOrDefault(c.getId(), new FicheCaisseLigne(c));
            BigDecimal fact = fcl.getMontantFacture();
            BigDecimal prev = fcl.getSoldePrecedent();
            BigDecimal mqnt = fcl.getManquant();
            BigDecimal mqntCumul = prev.add(mqnt);
            BigDecimal total = fact.add(mqntCumul);
            BigDecimal vers = fcl.getMontantVerse();
            BigDecimal reste = total.subtract(vers);
            BigDecimal vv = versementsVeille.getOrDefault(c.getId(), BigDecimal.ZERO);
            String visa = (fcl.getVersement() != null && fcl.getVersement().getCaissier() != null) ? fcl.getVersement().getCaissier().getNomComplet() : "—";
            dataMatrice.add(new CaisseRowData(c, fcl, fact, prev, mqnt, mqntCumul, total, vers, vv, reste, visa));
        }
        construireColonnesMatrice();
        recalculerTotauxFooter();
    }

    private void construireColonnesMatrice() {
        tableMatrice.getColumns().clear();

        TableColumn<CaisseRowData, String> colNom = new TableColumn<>("NOM LIVREUR / CLIENT");
        colNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().client.getNom()));
        colNom.setPrefWidth(220);
        colNom.getStyleClass().add("col-nom");
        tableMatrice.getColumns().add(colNom);

        TableColumn<CaisseRowData, String> colFact = new TableColumn<>("FACTURE");
        colFact.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().facture)));
        colFact.getStyleClass().add("col-facture");
        colFact.setPrefWidth(110);
        tableMatrice.getColumns().add(colFact);

        TableColumn<CaisseRowData, BigDecimal> colMqnt = new TableColumn<>("MANQUANT");
        colMqnt.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().manquantCumule));
        colMqnt.setCellFactory(col -> new TableCell<>() {
            private final TextField txt = new TextField();
            {
                txt.getStyleClass().add("matrice-cell-input");
                txt.setOnAction(e -> validerEtEnregistrerManquant(getIndex(), txt.getText()));
                txt.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validerEtEnregistrerManquant(getIndex(), txt.getText()); });
            }
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null); else { txt.setText(item != null && item.compareTo(BigDecimal.ZERO) != 0 ? item.toPlainString() : ""); setGraphic(txt); }
            }
        });
        colMqnt.getStyleClass().add("col-manquant");
        colMqnt.setPrefWidth(110);
        tableMatrice.getColumns().add(colMqnt);

        TableColumn<CaisseRowData, String> colTotal = new TableColumn<>("TOTAL\nSOLDE");
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().totalSolde)));
        colTotal.getStyleClass().add("col-total-solde");
        colTotal.setPrefWidth(110);
        tableMatrice.getColumns().add(colTotal);

        TableColumn<CaisseRowData, BigDecimal> colVers = new TableColumn<>("VERSEMENT");
        colVers.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().versement));
        colVers.setCellFactory(col -> new TableCell<>() {
            private final TextField txt = new TextField();
            {
                txt.getStyleClass().add("matrice-cell-input");
                txt.setOnAction(e -> validerEtEnregistrerVersement(getIndex(), txt.getText()));
                txt.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validerEtEnregistrerVersement(getIndex(), txt.getText()); });
            }
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null); else { txt.setText(item != null && item.compareTo(BigDecimal.ZERO) > 0 ? item.toPlainString() : ""); setGraphic(txt); }
            }
        });
        colVers.getStyleClass().add("col-versement");
        colVers.setPrefWidth(110);
        tableMatrice.getColumns().add(colVers);

        TableColumn<CaisseRowData, String> colReste = new TableColumn<>("RESTE / ÉCART");
        colReste.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().reste)));
        colReste.getStyleClass().add("col-reste");
        colReste.setPrefWidth(110);
        tableMatrice.getColumns().add(colReste);

        if (session.isAdmin()) {
            TableColumn<CaisseRowData, String> colVisa = new TableColumn<>("VISA");
            colVisa.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().visa));
            colVisa.getStyleClass().add("col-visa");
            colVisa.setPrefWidth(140);
            tableMatrice.getColumns().add(colVisa);
        }
    }

    private void validerEtEnregistrerVersement(int idx, String txt) {
        if (idx < 0 || idx >= dataMatrice.size()) return;
        CaisseRowData r = dataMatrice.get(idx);
        BigDecimal val = BigDecimal.ZERO; try { if (txt != null && !txt.isBlank()) val = new BigDecimal(txt.trim().replace(",",".").replace(" ","")); } catch (Exception ignored) {}
        if (val.compareTo(r.versement) == 0) return;
        final BigDecimal valFinal = val;
        final String me = session.getUtilisateur() != null ? session.getUtilisateur().getNomComplet() : "Caissier";
        POOL.submit(() -> {
            try {
                versementDAO.enregistrerOuMettreAJourVersement(currentFiche.getId(), r.client.getId(), currentFiche.getDateFiche(), valFinal, session.getUserId());
                Platform.runLater(() -> { r.versement = valFinal; r.visa = me; r.recalculer(); tableMatrice.refresh(); recalculerTotauxFooter(); });
            } catch (Exception ex) { Platform.runLater(() -> setSyncStatus("❌ " + ex.getMessage(), "red")); }
        });
    }

    private void validerEtEnregistrerManquant(int idx, String txt) {
        if (idx < 0 || idx >= dataMatrice.size()) return;
        CaisseRowData r = dataMatrice.get(idx);
        BigDecimal val = BigDecimal.ZERO; try { if (txt != null && !txt.isBlank()) val = new BigDecimal(txt.trim().replace(",",".").replace(" ","")); } catch (Exception ignored) {}
        if (val.compareTo(r.manquantCumule) == 0) return;
        r.manquantCumule = val; r.recalculer(); tableMatrice.refresh(); recalculerTotauxFooter();
    }

    private void recalculerTotauxFooter() {
        BigDecimal tf = dataMatrice.stream().map(r -> r.facture).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tv = dataMatrice.stream().map(r -> r.versement).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tr = dataMatrice.stream().map(r -> r.reste).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalsFooter.setText("TOTAL FACTURES : " + FormatUtil.montant(tf) + " | TOTAL VERSÉ : " + FormatUtil.montant(tv) + " | TOTAL RESTE : " + FormatUtil.montant(tr));
        lblTotalFactures.setText("Total Factures\n" + FormatUtil.montant(tf) + " FCFA");
        lblTotalVerses.setText("Total Versé\n" + FormatUtil.montant(tv) + " FCFA");
        lblTotalRestes.setText("Total Reste\n" + FormatUtil.montant(tr) + " FCFA");
    }

    private void setSyncStatus(String t, String c) { lblSyncStatus.setText(t); lblSyncStatus.setStyle("-fx-text-fill: " + c + "; -fx-font-weight: bold;"); }

    private void chargerListeFiches() {
        runAsync(() -> ficheDAO.findByFilters(dpDu.getValue(), dpAu.getValue(), null, null, "CAISSE"), list -> listeFichesObs.setAll(list));
    }

    private void nouvelleCaisse() {
        DatePicker dp = new DatePicker(LocalDate.now());
        Dialog<LocalDate> dlg = new Dialog<>(); dlg.setTitle("Nouvelle Caisse"); 
        VBox vb = new VBox(10, new Label("Date de la feuille :"), dp);
        vb.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(vb);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(b -> b == ButtonType.OK ? dp.getValue() : null);
        dlg.showAndWait().ifPresent(d -> { chargerOuCreerCaisse(d); afficherVueDetail(); });
    }

    private void ouvrirCaisse(FicheJournaliere f) { chargerOuCreerCaisse(f.getDateFiche()); afficherVueDetail(); }

    private void afficherVueListe() { rootView.getChildren().clear(); rootView.getChildren().add(vueListeFiches); chargerListeFiches(); }

    private void afficherVueDetail() { rootView.getChildren().clear(); rootView.getChildren().add(vueDetailCaisse); }

    @Override public void refresh() { if (currentFiche != null) chargerOuCreerCaisse(currentFiche.getDateFiche()); else chargerListeFiches(); }

    public static class CaisseRowData {
        public final Client client; public final FicheCaisseLigne ficheLigne; public BigDecimal facture, ecartPrecedent, manquantJour, manquantCumule, totalSolde, versement, versementVeille, reste; public String visa;
        public CaisseRowData(Client c, FicheCaisseLigne fl, BigDecimal f, BigDecimal ep, BigDecimal mj, BigDecimal mc, BigDecimal ts, BigDecimal v, BigDecimal vv, BigDecimal r, String vi) {
            this.client = c; this.ficheLigne = fl; this.facture = f; this.ecartPrecedent = ep; this.manquantJour = mj; this.manquantCumule = mc; this.totalSolde = ts; this.versement = v; this.versementVeille = vv; this.reste = r; this.visa = vi;
        }
        public void recalculer() { this.totalSolde = facture.add(manquantCumule); this.reste = totalSolde.subtract(versement); }
    }
}
