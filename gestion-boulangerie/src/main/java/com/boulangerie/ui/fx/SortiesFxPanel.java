package com.boulangerie.ui.fx;

import com.boulangerie.dao.ClientDAO;
import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.dao.TarifClientDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.ExcelExportService;
import com.boulangerie.service.PdfService;
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
import javafx.stage.FileChooser;
import atlantafx.base.theme.Styles;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module SORTIES — Matrice Journalière 100% Dynamique
 * - Gestion des quantités et de la facture brute uniquement.
 * - Suivi des modifications par ligne (Visa).
 * - S.VIDE supprimé des colonnes.
 */
public class SortiesFxPanel extends FxPanelBase {

    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final TarifClientDAO tarifDAO = new TarifClientDAO();
    private final SessionService session = SessionService.getInstance();

    private StackPane rootView;
    private VBox vueListeFiches;
    private VBox vueDetailFiche;
    private DatePicker dpDuListe, dpAuListe;
    private TableView<FicheJournaliere> tableListeFiches;
    private final ObservableList<FicheJournaliere> listeFichesObs = FXCollections.observableArrayList();

    // En-tête date & statut (vue détail)
    private Label lblNumeroFiche;
    private Label lblSyncStatus;

    // KPI Banner
    private Label lblTotalSorties, lblTotalRetours, lblTotalBrut;

    // Onglets de catégories dynamiques
    private TabPane tabsCategories;
    private List<CategorieClient> categoriesList = new ArrayList<>();
    private CategorieClient activeCategorie = null;

    // Données en mémoire
    private FicheJournaliere currentFiche;
    private List<Produit> currentProduits = new ArrayList<>();
    private List<Client> allClients = new ArrayList<>();
    private List<LigneCommande> currentLignes = new ArrayList<>();

    // Table et données
    private final ObservableList<MatriceRowData> dataMatrice = FXCollections.observableArrayList();
    private TableView<MatriceRowData> tableMatrice;
    private Label lblTotalsFooter;

    public SortiesFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        rootView = new StackPane();
        root.setCenter(rootView);

        buildListeFichesView();
        buildDetailView();

        afficherVueListe();
    }

    private void buildListeFichesView() {
        vueListeFiches = new VBox(10);
        vueListeFiches.setPadding(new Insets(14));
        vueListeFiches.getStyleClass().add("main-container");

        Label lblTitre = new Label("📋 SORTIES — Fiches journalières");
        lblTitre.getStyleClass().add("section-title");

        Region accent = new Region();
        accent.setStyle("-fx-background-color: #F5A623; -fx-pref-height:3; -fx-max-height:3; -fx-background-radius:2; -fx-pref-width:50;");

        HBox filtreBar = new HBox(10);
        filtreBar.setAlignment(Pos.CENTER_LEFT);
        filtreBar.getStyleClass().add("card");
        filtreBar.setPadding(new Insets(10));

        dpDuListe = new DatePicker(LocalDate.now().minusDays(30));
        dpAuListe = new DatePicker(LocalDate.now().plusDays(7));
        Button btnRech = btnPrimary("🔍 Appliquer", BootstrapIcons.FILTER);
        btnRech.setOnAction(e -> chargerListeFiches());

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnNouv = btnPrimary("➕ Nouvelle fiche", BootstrapIcons.PLUS_CIRCLE);
        btnNouv.setOnAction(e -> nouvelleFicheSortie());

        filtreBar.getChildren().addAll(new Label("Du :"), dpDuListe, new Label("Au :"), dpAuListe, btnRech, sp, btnNouv);

        tableListeFiches = new TableView<>();
        tableListeFiches.setItems(listeFichesObs);
        tableListeFiches.getStyleClass().addAll("styled-table", Styles.STRIPED);

        TableColumn<FicheJournaliere, String> colNum = new TableColumn<>("N° Fiche");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colNum.setPrefWidth(140);

        TableColumn<FicheJournaliere, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.date(d.getValue().getDateFiche())));
        colDate.setPrefWidth(100);

        TableColumn<FicheJournaliere, String> colNet = new TableColumn<>("Total Net HT");
        colNet.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().getTotalNet())));
        colNet.setPrefWidth(130);
        colNet.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        TableColumn<FicheJournaliere, Void> colAction = new TableColumn<>("Action");
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnOpen = btnPrimary("Ouvrir", BootstrapIcons.FOLDER2_OPEN);
            private final Button btnSuppr = btnDanger("", BootstrapIcons.TRASH);
            {
                btnOpen.setOnAction(e -> { FicheJournaliere f = getTableView().getItems().get(getIndex()); if (f != null) ouvrirFiche(f); });
                btnSuppr.setOnAction(e -> { FicheJournaliere f = getTableView().getItems().get(getIndex()); if (f != null) supprimerFiche(f); });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null); else { HBox h = new HBox(5, btnOpen, btnSuppr); h.setAlignment(Pos.CENTER); setGraphic(h); }
            }
        });

        tableListeFiches.getColumns().addAll(colNum, colDate, colNet, colAction);
        VBox.setVgrow(tableListeFiches, Priority.ALWAYS);

        vueListeFiches.getChildren().addAll(lblTitre, accent, filtreBar, tableListeFiches);
    }

    private void buildDetailView() {
        vueDetailFiche = new VBox(8);
        vueDetailFiche.setPadding(new Insets(10));
        vueDetailFiche.getStyleClass().add("main-container");

        Button btnRetour = btnOutline("← Retour à la liste");
        btnRetour.setOnAction(e -> afficherVueListe());

        lblNumeroFiche = new Label("N° —");
        lblNumeroFiche.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        Label lblTitrePage = new Label("FEUILLE DE SORTIES JOURNALIÈRE");
        lblTitrePage.getStyleClass().add("section-title");
        lblTitrePage.setStyle("-fx-font-size: 16px;");

        lblSyncStatus = new Label("🟢 Prêt");
        lblSyncStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topBar = new HBox(12, btnRetour, lblTitrePage, lblNumeroFiche, sp, lblSyncStatus);
        topBar.setAlignment(Pos.CENTER_LEFT);

        lblTotalSorties = kpiCardLabel("Total Pains Sortis", "0 pcs", "-app-bleu-nuit");
        lblTotalRetours = kpiCardLabel("Total Retours", "0 pcs", "-app-rouge");
        lblTotalBrut    = kpiCardLabel("Total Facture Brut", "0 FCFA", "-app-vert");
        HBox kpiBox = new HBox(10, lblTotalSorties, lblTotalRetours, lblTotalBrut);

        HBox addBar = buildInlineAddBar();

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

        vueDetailFiche.getChildren().addAll(topBar, kpiBox, addBar, tabsCategories, tableMatrice, lblTotalsFooter);
        VBox.setVgrow(tableMatrice, Priority.ALWAYS);
    }

    private Label kpiCardLabel(String titre, String valeur, String couleurVar) {
        Label lbl = new Label(titre + "\n" + valeur);
        lbl.getStyleClass().add("card");
        lbl.setStyle("-fx-padding: 10; -fx-font-weight: bold; -fx-text-fill: " + couleurVar + "; -fx-alignment: CENTER-LEFT;");
        lbl.setPrefWidth(200);
        return lbl;
    }

    private HBox buildInlineAddBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("card");

        ComboBox<Client> cbo = new ComboBox<>();
        cbo.setPrefWidth(300);
        cbo.setPromptText("Ajouter un client à la feuille...");
        Button btn = btnPrimary("Insérer la ligne", BootstrapIcons.PLUS_CIRCLE);
        btn.setOnAction(e -> { if (cbo.getValue() != null) { ajouterClientALaMatrice(cbo.getValue()); cbo.setValue(null); } });

        bar.getChildren().addAll(new Label("👤"), cbo, btn);
        POOL.submit(() -> { List<Client> cls = clientDAO.findAll(); Platform.runLater(() -> cbo.setItems(FXCollections.observableArrayList(cls))); });
        return bar;
    }

    private void chargerOuCreerFiche(LocalDate date) {
        setSyncStatus("⏳ Chargement...", "#E37400");
        runAsync(() -> {
            FicheJournaliere f = ficheDAO.getOrCreateFicheJour(date, "SORTIE", session.getUserId());
            List<Produit> prods = produitDAO.findAll(false).stream()
                .filter(p -> !p.getLibelle().toUpperCase().contains("S.VIDE") && !p.getCode().toUpperCase().contains("S.VIDE"))
                .collect(Collectors.toList());
            List<Client> clients = clientDAO.findAll();
            List<CategorieClient> cats = clientDAO.findAllCategories();
            List<LigneCommande> lignes = ficheDAO.findLignesByDate(date);
            return new Object[]{f, prods, clients, cats, lignes};
        }, res -> {
            this.currentFiche = (FicheJournaliere) res[0];
            this.currentProduits = (List<Produit>) res[1];
            this.allClients = (List<Client>) res[2];
            this.categoriesList = (List<CategorieClient>) res[3];
            this.currentLignes = (List<LigneCommande>) res[4];

            lblNumeroFiche.setText("Fiche N° : " + currentFiche.getNumero());
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

        Map<String, List<LigneCommande>> map = currentLignes.stream().filter(l -> l.getClient() != null).collect(Collectors.groupingBy(l -> l.getClient().getId()));

        dataMatrice.clear();
        for (Client c : filtres) {
            List<LigneCommande> lc = map.getOrDefault(c.getId(), new ArrayList<>());
            BigDecimal fact = lc.stream().map(LigneCommande::getMontantHt).reduce(BigDecimal.ZERO, BigDecimal::add);
            String lastMod = lc.isEmpty() ? "—" : lc.get(0).getModifiePar();
            dataMatrice.add(new MatriceRowData(c, lc, fact, lastMod));
        }
        construireColonnesMatrice();
        recalculerTotauxFooter();
    }

    private void construireColonnesMatrice() {
        tableMatrice.getColumns().clear();

        TableColumn<MatriceRowData, String> colNom = new TableColumn<>("NOM LIVREUR / CLIENT");
        colNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().client.getNom()));
        colNom.setPrefWidth(220);
        colNom.getStyleClass().add("col-nom");
        tableMatrice.getColumns().add(colNom);

        for (Produit p : currentProduits) {
            TableColumn<MatriceRowData, Integer> colP = new TableColumn<>(p.getLibelle());
            colP.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getQuantiteForProduit(p.getId())));
            colP.setCellFactory(col -> new TableCell<>() {
                private final TextField txt = new TextField();
                {
                    txt.getStyleClass().add("matrice-cell-input");
                    txt.getStyleClass().add("qte-input");
                    txt.setOnAction(e -> validerEtEnregistrerQte(getIndex(), p, txt.getText()));
                    txt.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validerEtEnregistrerQte(getIndex(), p, txt.getText()); });
                }
                @Override protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) setGraphic(null); else { txt.setText(item != null && item > 0 ? String.valueOf(item) : ""); setGraphic(txt); }
                }
            });
            colP.setPrefWidth(65);
            tableMatrice.getColumns().add(colP);
        }

        TableColumn<MatriceRowData, String> colFact = new TableColumn<>("FACTURE");
        colFact.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().facture)));
        colFact.getStyleClass().add("col-facture");
        colFact.setPrefWidth(120);
        tableMatrice.getColumns().add(colFact);

        if (session.isAdmin()) {
            TableColumn<MatriceRowData, String> colVisa = new TableColumn<>("VISA");
            colVisa.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().modifiePar != null ? d.getValue().modifiePar : "—"));
            colVisa.getStyleClass().add("col-visa");
            colVisa.setPrefWidth(140);
            tableMatrice.getColumns().add(colVisa);
        }
    }

    private void validerEtEnregistrerQte(int rowIndex, Produit produit, String texte) {
        if (rowIndex < 0 || rowIndex >= dataMatrice.size()) return;
        MatriceRowData row = dataMatrice.get(rowIndex);
        int qte = 0; try { if (texte != null && !texte.isBlank()) qte = Integer.parseInt(texte.trim()); } catch (Exception ignored) {}
        if (qte == row.getQuantiteForProduit(produit.getId())) return;

        final int qFinal = qte;
        final String user = session.getUtilisateur() != null ? session.getUtilisateur().getNomComplet() : "Inconnu";

        POOL.submit(() -> {
            try {
                LigneCommande l = row.findLigneForProduit(produit.getId());
                if (l == null && qFinal > 0) {
                    l = new LigneCommande(); l.setFicheId(currentFiche.getId()); l.setClient(row.client); l.setProduit(produit);
                    l.setQuantiteSortie(qFinal); l.setModifiePar(user);
                    BigDecimal prix = tarifDAO.findPrixSpecifique(row.client.getId(), produit.getId(), currentFiche.getDateFiche()).orElse(produit.getPrixUnitaire());
                    l.setTarifApplicable(prix); l.setMontantHt(prix.multiply(BigDecimal.valueOf(qFinal)));
                    ficheDAO.saveLigne(l); row.lignes.add(l);
                } else if (l != null) {
                    if (qFinal > 0) { l.setQuantiteSortie(qFinal); l.setModifiePar(user); l.setMontantHt(l.getTarifApplicable().multiply(BigDecimal.valueOf(qFinal))); ficheDAO.updateLigne(l); }
                    else { ficheDAO.deleteLigne(l.getId()); row.lignes.remove(l); }
                }
                ficheDAO.recalculerTotauxFiche(currentFiche.getId());
                Platform.runLater(() -> { row.recalculer(); row.modifiePar = user; tableMatrice.refresh(); recalculerTotauxFooter(); });
            } catch (Exception ex) { Platform.runLater(() -> setSyncStatus("❌ Erreur : " + ex.getMessage(), "red")); }
        });
    }

    private void ajouterClientALaMatrice(Client client) {
        if (dataMatrice.stream().anyMatch(r -> r.client.getId().equals(client.getId()))) return;
        dataMatrice.add(0, new MatriceRowData(client, new ArrayList<>(), BigDecimal.ZERO, "—"));
        recalculerTotauxFooter();
    }

    private void recalculerTotauxFooter() {
        BigDecimal tot = dataMatrice.stream().map(r -> r.facture).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalsFooter.setText("TOTAL LIGNES : " + dataMatrice.size() + " | FACTURE TOTALE : " + FormatUtil.montant(tot) + " FCFA");
        lblTotalBrut.setText("Total Facture Brut\n" + FormatUtil.montant(tot) + " FCFA");
    }

    private void setSyncStatus(String text, String color) { lblSyncStatus.setText(text); lblSyncStatus.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;"); }

    private void chargerListeFiches() {
        LocalDate du = dpDuListe.getValue(); LocalDate au = dpAuListe.getValue();
        runAsync(() -> ficheDAO.findByFilters(du, au, null, null, "SORTIE"), list -> listeFichesObs.setAll(list));
    }

    private void nouvelleFicheSortie() {
        DatePicker dp = new DatePicker(LocalDate.now());
        Dialog<LocalDate> dlg = new Dialog<>(); dlg.setTitle("Nouvelle Fiche SORTIE"); 
        VBox vb = new VBox(10, new Label("Date de la fiche :"), dp);
        vb.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(vb);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(b -> b == ButtonType.OK ? dp.getValue() : null);
        dlg.showAndWait().ifPresent(d -> { chargerOuCreerFiche(d); afficherVueDetail(); });
    }

    private void ouvrirFiche(FicheJournaliere f) { chargerOuCreerFiche(f.getDateFiche()); afficherVueDetail(); }

    private void supprimerFiche(FicheJournaliere f) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer la fiche " + f.getNumero() + " ?");
        c.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> runAsync(() -> { ficheDAO.deleteFiche(f.getId()); return true; }, ok -> chargerListeFiches()));
    }

    private void afficherVueListe() { rootView.getChildren().clear(); rootView.getChildren().add(vueListeFiches); chargerListeFiches(); }

    private void afficherVueDetail() { rootView.getChildren().clear(); rootView.getChildren().add(vueDetailFiche); }

    @Override public void refresh() { if (currentFiche != null) chargerOuCreerFiche(currentFiche.getDateFiche()); else chargerListeFiches(); }

    public static class MatriceRowData {
        public final Client client; public final List<LigneCommande> lignes; public BigDecimal facture; public String modifiePar;
        public MatriceRowData(Client c, List<LigneCommande> l, BigDecimal f, String m) { this.client = c; this.lignes = new ArrayList<>(l); this.facture = f; this.modifiePar = m; }
        public int getQuantiteForProduit(String pId) { return lignes.stream().filter(l -> l.getProduit() != null && pId.equals(l.getProduit().getId())).mapToInt(LigneCommande::getQuantiteNette).sum(); }
        public LigneCommande findLigneForProduit(String pId) { return lignes.stream().filter(l -> l.getProduit() != null && pId.equals(l.getProduit().getId())).findFirst().orElse(null); }
        public void recalculer() { this.facture = lignes.stream().map(LigneCommande::getMontantHt).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }
}
