package com.boulangerie.ui.fx;

import com.boulangerie.dao.ClientDAO;
import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.dao.TarifClientDAO;
import com.boulangerie.dao.VersementDAO;
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
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module Sorties & Commandes — Matrice Journalière 100% Dynamique
 * - Catégories dynamiques créées à partir de la base de données (avec onglets correspondants).
 * - Colonnes de produits dynamiques s'ajustant automatiquement dès l'ajout de nouveaux produits.
 * - En-tête hiérarchique : Catégorie en haut (TYPE DE PAIN, PJ, etc.) et Noms de pain en sous-colonnes.
 * - Bordures noires nettes (type papier/Excel) sur chaque cellule et colonne.
 * - 100% Inline — Saisie directe au clavier, zéro popup.
 */
public class SortiesFxPanel extends FxPanelBase {

    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final TarifClientDAO tarifDAO = new TarifClientDAO();
    private final VersementDAO versementDAO = new VersementDAO();
    private final SessionService session = SessionService.getInstance();

    // En-tête date & statut
    private DatePicker dpDateFiche;
    private Label lblNumeroFiche;
    private Label lblSyncStatus;

    // KPI Banner
    private Label lblTotalSorties, lblTotalRetours, lblTotalNet, lblTotalVersements, lblTotalDette;

    // Onglets de catégories dynamiques
    private TabPane tabsCategories;
    private List<CategorieClient> categoriesList = new ArrayList<>();
    private CategorieClient activeCategorie = null; // null = Tous les clients

    // Données en mémoire
    private FicheJournaliere currentFiche;
    private List<Produit> currentProduits = new ArrayList<>();
    private List<Client> allClients = new ArrayList<>();
    private List<LigneCommande> currentLignes = new ArrayList<>();
    private Map<String, BigDecimal> versementsJour = new HashMap<>();
    private Map<String, BigDecimal> versementsVeille = new HashMap<>();

    // Table et données
    private ObservableList<MatriceRowData> dataMatrice = FXCollections.observableArrayList();
    private TableView<MatriceRowData> tableMatrice;
    private Label lblTotalsFooter;

    public SortiesFxPanel(MainWindow mainWindow) {
        super(mainWindow);
        buildUI();
    }

    private void buildUI() {
        VBox mainContainer = new VBox(8);
        mainContainer.setPadding(new Insets(10));
        mainContainer.setStyle("-fx-background-color: #F8F9FA;");

        // ═════════════════════════════════════════════════════════════
        // 1. BARRE D'OUTILS SUPÉRIEURE (Date directe, zéro popup)
        // ═════════════════════════════════════════════════════════════
        Label lblTitre = new Label("Fiche Journalière de Sortie :");
        lblTitre.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1F3A5F;");

        dpDateFiche = new DatePicker(LocalDate.now());
        dpDateFiche.setPrefWidth(140);
        dpDateFiche.setOnAction(e -> chargerOuCreerFiche(dpDateFiche.getValue()));

        lblNumeroFiche = new Label("N° —");
        lblNumeroFiche.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        Button btnPDF = btnOutline("📄 Export PDF");
        Button btnExcel = btnOutline("📊 Export Excel");
        btnPDF.setOnAction(e -> exporterPDF());
        btnExcel.setOnAction(e -> exporterExcel());

        lblSyncStatus = new Label("🟢 Prêt");
        lblSyncStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");

        Region spacerTop = new Region();
        HBox.setHgrow(spacerTop, Priority.ALWAYS);

        HBox topBar = new HBox(12, lblTitre, dpDateFiche, lblNumeroFiche, new Separator(Orientation.VERTICAL), btnPDF, btnExcel, spacerTop, lblSyncStatus);
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ═════════════════════════════════════════════════════════════
        // 2. BANNIÈRE KPI (Indicateurs en temps réel)
        // ═════════════════════════════════════════════════════════════
        lblTotalSorties    = kpiCard("Total Pains Sortis", "0 pièces", "#1F3A5F");
        lblTotalRetours    = kpiCard("Total Retours", "0 pièces", "#C62828");
        lblTotalNet        = kpiCard("Total Facture Net", "0 FCFA", "#2E7D32");
        lblTotalVersements = kpiCard("Versements Reçus", "0 FCFA", "#1565C0");
        lblTotalDette      = kpiCard("Reste Net à Encaisser", "0 FCFA", "#D32F2F");

        HBox kpiBox = new HBox(10, lblTotalSorties, lblTotalRetours, lblTotalNet, lblTotalVersements, lblTotalDette);

        // ═════════════════════════════════════════════════════════════
        // 3. BARRE D'AJOUT RAPIDE INLINE (Sans popup)
        // ═════════════════════════════════════════════════════════════
        HBox inlineAddBar = buildInlineAddBar();

        // ═════════════════════════════════════════════════════════════
        // 4. ONGLETS PAR CATÉGORIE (100% DYNAMIQUES DEPUIS LA DB)
        // ═════════════════════════════════════════════════════════════
        tabsCategories = new TabPane();
        tabsCategories.getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);
        tabsCategories.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                this.activeCategorie = (CategorieClient) nv.getUserData();
                filtrerEtAfficherMatrice();
            }
        });

        // ═════════════════════════════════════════════════════════════
        // 5. TABLE MATRICIELLE AVEC BORDURES NOIRES (TYPE EXCEL)
        // ═════════════════════════════════════════════════════════════
        VBox tableBox = new VBox(4);
        tableMatrice = new TableView<>();
        tableMatrice.getStyleClass().addAll("matrice-excel-table", "styled-table");
        tableMatrice.setStyle("-fx-background-color: white; -fx-table-cell-border-color: #000000; -fx-border-color: #000000; -fx-border-width: 1.5px;");
        tableMatrice.setItems(dataMatrice);
        tableMatrice.setEditable(true);
        tableBox.getChildren().add(tableMatrice);
        VBox.setVgrow(tableMatrice, Priority.ALWAYS);

        lblTotalsFooter = new Label("TOTAL : 0 ligne(s)");
        lblTotalsFooter.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #000000; -fx-padding: 8 10; "
            + "-fx-background-color: #E2E8F0; -fx-border-color: #000000; -fx-border-width: 1.5px; -fx-background-radius: 4; -fx-border-radius: 4;");

        mainContainer.getChildren().addAll(topBar, kpiBox, inlineAddBar, tabsCategories, tableBox, lblTotalsFooter);
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        root.setCenter(mainContainer);

        // Chargement initial
        chargerOuCreerFiche(LocalDate.now());
    }

    private Label kpiCard(String titre, String valeur, String couleur) {
        Label lbl = new Label(titre + "\n" + valeur);
        lbl.setStyle("-fx-background-color: white; -fx-border-color: #000000; -fx-border-width: 1px; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 12; -fx-font-size: 11px; -fx-text-fill: " + couleur + "; "
            + "-fx-line-spacing: 2px; -fx-font-weight: bold;");
        lbl.setPrefWidth(180);
        return lbl;
    }

    private HBox buildInlineAddBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #000000; -fx-border-width: 1px; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 12;");

        Label lblAdd = new Label("➕ Ajouter un client dans la feuille :");
        lblAdd.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #000000;");

        ComboBox<Client> cboClient = new ComboBox<>();
        cboClient.setPrefWidth(320);
        cboClient.setPromptText("Sélectionnez ou tapez le nom du client...");

        Button btnInserer = btnPrimary("Insérer la ligne", BootstrapIcons.PLUS_CIRCLE);

        btnInserer.setOnAction(e -> {
            Client sel = cboClient.getValue();
            if (sel != null) {
                ajouterClientALaMatrice(sel);
                cboClient.setValue(null);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblAide = new Label("💡 Saisie directe : Cliquez dans les cases pour saisir ou modifier les quantités et versements.");
        lblAide.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151; -fx-font-style: italic;");

        bar.getChildren().addAll(lblAdd, cboClient, btnInserer, spacer, lblAide);

        POOL.submit(() -> {
            List<Client> cls = clientDAO.findAll();
            Platform.runLater(() -> {
                this.allClients = cls;
                cboClient.setItems(FXCollections.observableArrayList(cls));
            });
        });

        return bar;
    }

    private void chargerOuCreerFiche(LocalDate date) {
        setSyncStatus("⏳ Chargement...", "#E37400");
        runAsync(() -> {
            FicheJournaliere f = ficheDAO.getOrCreateFicheJour(date, session.getUserId());
            List<Produit> prods = produitDAO.findAll(false);
            List<Client> clients = clientDAO.findAll();
            List<CategorieClient> cats = clientDAO.findAllCategories();
            List<LigneCommande> lignes = ficheDAO.findLignesByDate(date);
            Map<String, BigDecimal> vJour = versementDAO.findTotalVersementsByDate(date);
            Map<String, BigDecimal> vVeille = versementDAO.findTotalVersementsByDate(date.minusDays(1));
            return new Object[]{f, prods, clients, cats, lignes, vJour, vVeille};
        }, res -> {
            this.currentFiche = (FicheJournaliere) res[0];
            this.currentProduits = (List<Produit>) res[1];
            this.allClients = (List<Client>) res[2];
            this.categoriesList = (List<CategorieClient>) res[3];
            this.currentLignes = (List<LigneCommande>) res[4];
            this.versementsJour = (Map<String, BigDecimal>) res[5];
            this.versementsVeille = (Map<String, BigDecimal>) res[6];

            lblNumeroFiche.setText("N° " + currentFiche.getNumero());
            setSyncStatus("🟢 Prêt", "#2E7D32");

            mettreAJourOngletsCategories();
            filtrerEtAfficherMatrice();
        });
    }

    /**
     * Reconstruit dynamiquement les onglets selon les catégories présentes en base de données.
     * Si une nouvelle catégorie est ajoutée, elle apparaît automatiquement ici !
     */
    private void mettreAJourOngletsCategories() {
        CategorieClient currentSel = activeCategorie;
        tabsCategories.getTabs().clear();

        for (CategorieClient cat : categoriesList) {
            String label = switch (cat.getNom().toUpperCase().trim()) {
                case "CARREFOUR" -> "AGENCE CARREF";
                case "EXTERNE"   -> "LIVREUR EXTERNE";
                case "INTERNE"   -> "LIVREUR INTERNE";
                default          -> cat.getNom().toUpperCase();
            };

            Tab tab = new Tab(label);
            tab.setUserData(cat);
            tab.setClosable(false);
            tab.setContent(new Region());
            tabsCategories.getTabs().add(tab);

            if (currentSel != null && cat.getId().equals(currentSel.getId())) {
                tabsCategories.getSelectionModel().select(tab);
            }
        }

        // Onglet global "TOUS LES CLIENTS"
        Tab tabTous = new Tab("TOUS LES CLIENTS");
        tabTous.setUserData(null);
        tabTous.setClosable(false);
        tabTous.setContent(new Region());
        tabsCategories.getTabs().add(tabTous);

        if (currentSel == null && !tabsCategories.getTabs().isEmpty()) {
            tabsCategories.getSelectionModel().select(0);
            this.activeCategorie = (CategorieClient) tabsCategories.getTabs().get(0).getUserData();
        }
    }

    private void filtrerEtAfficherMatrice() {
        // Filtrer les clients de la catégorie active
        List<Client> clientsFiltres = allClients.stream()
            .filter(c -> {
                if (activeCategorie == null) return true;
                return c.getCategorie() != null && activeCategorie.getId().equals(c.getCategorie().getId());
            })
            .sorted(Comparator.comparing(c -> c.getNom() != null ? c.getNom() : ""))
            .collect(Collectors.toList());

        Map<String, List<LigneCommande>> lignesByClient = currentLignes.stream()
            .filter(l -> l.getClient() != null)
            .collect(Collectors.groupingBy(l -> l.getClient().getId()));

        dataMatrice.clear();

        for (Client c : clientsFiltres) {
            List<LigneCommande> cmdClient = lignesByClient.getOrDefault(c.getId(), Collections.emptyList());

            BigDecimal facture = cmdClient.stream()
                .map(LigneCommande::getMontantHt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal vJour = versementsJour.getOrDefault(c.getId(), BigDecimal.ZERO);
            BigDecimal vVeille = versementsVeille.getOrDefault(c.getId(), BigDecimal.ZERO);

            BigDecimal soldeActuel = c.getSoldeActuel() != null ? c.getSoldeActuel() : BigDecimal.ZERO;
            BigDecimal ecartPrecedent = soldeActuel.subtract(facture).add(vJour);
            BigDecimal totalSolde = facture.add(ecartPrecedent);
            BigDecimal ecartVersement = totalSolde.subtract(vJour);

            MatriceRowData row = new MatriceRowData(c, cmdClient, facture, ecartPrecedent, BigDecimal.ZERO, totalSolde, vJour, vVeille, ecartVersement);
            dataMatrice.add(row);
        }

        construireColonnesMatriceDynamique();
        recalculerTotauxFooter();
    }

    /**
     * Construction 100% DYNAMIQUE des colonnes avec BORDURES NOIRES NETTES :
     * - Catégorie en haut (TYPE DE PAIN, PJ, etc.) et Noms de pain en sous-colonnes (40, 80, 100, 160...).
     * - Si de nouveaux produits sont ajoutés dans la base, ils s'insèrent automatiquement dans la colonne adéquate !
     */
    private void construireColonnesMatriceDynamique() {
        tableMatrice.getColumns().clear();

        String borderStyle = "-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-font-weight: bold; -fx-alignment: CENTER;";

        // 1. Colonne NOM (NOM AGEN CARREF DIV ou NOM LIVREUR)
        String nomHeader = activeCategorie != null && ("EXTERNE".equalsIgnoreCase(activeCategorie.getNom()) || "INTERNE".equalsIgnoreCase(activeCategorie.getNom()))
            ? "NOM LIVREUR" : "NOM\nAGEN CARREF DIV";

        TableColumn<MatriceRowData, String> colNom = new TableColumn<>(nomHeader);
        colNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().client.getNom()));
        colNom.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(borderStyle);
                } else {
                    setText(item);
                    // Comptes d'apurement 'Z ' en rouge vif avec texte blanc
                    if (item.trim().toUpperCase().startsWith("Z ") || item.trim().toUpperCase().startsWith("Z.")) {
                        setStyle("-fx-background-color: #D32F2F; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; "
                            + "-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-alignment: CENTER-LEFT; -fx-padding: 3 6;");
                    } else {
                        setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-font-weight: bold; "
                            + "-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-alignment: CENTER-LEFT; -fx-padding: 3 6;");
                    }
                }
            }
        });
        colNom.setPrefWidth(190);
        tableMatrice.getColumns().add(colNom);

        // 2. Colonnes Produits groupées dynamiquement
        // Organiser les produits de la base de données en groupes logiques
        String catNom = activeCategorie != null ? activeCategorie.getNom().toUpperCase() : "";

        if ("EXTERNE".equals(catNom)) {
            // Configuration Externe (avec 75, 160 GL, 160 GC...)
            TableColumn<MatriceRowData, String> grpPain = createHeaderGroup("TYPE DE PAIN");
            List<String> extNames = List.of("40", "75", "100", "160 GL", "160 GC", "EXTRA");
            for (String name : extNames) grpPain.getColumns().add(creerColonneProduitDynamique(name));
            tableMatrice.getColumns().add(grpPain);

            TableColumn<MatriceRowData, String> grpPj = createHeaderGroup("PJ");
            grpPj.getColumns().add(creerColonneProduitDynamique("80"));
            tableMatrice.getColumns().add(grpPj);

            TableColumn<MatriceRowData, Integer> colSv = creerColonneProduitDynamique("S.VIDE");
            colSv.setText("S.VIDE\n100");
            tableMatrice.getColumns().add(colSv);

        } else if ("INTERNE".equals(catNom)) {
            // Configuration Interne (Pain Nuit et Pain Jour)
            TableColumn<MatriceRowData, String> grpNuit = createHeaderGroup("TYPE DE PAIN NUIT");
            for (String name : List.of("40", "80", "100", "160")) {
                grpNuit.getColumns().add(creerColonneProduitDynamique(name));
            }
            tableMatrice.getColumns().add(grpNuit);

            TableColumn<MatriceRowData, String> grpJour = createHeaderGroup("TYPE DE PAIN JOUR");
            for (String name : List.of("40", "80", "125", "160", "EXTRA")) {
                grpJour.getColumns().add(creerColonneProduitDynamique(name));
            }
            tableMatrice.getColumns().add(grpJour);

            TableColumn<MatriceRowData, String> grpPj = createHeaderGroup("PJ");
            grpPj.getColumns().add(creerColonneProduitDynamique("80"));
            tableMatrice.getColumns().add(grpPj);

            TableColumn<MatriceRowData, Integer> colSv = creerColonneProduitDynamique("S.VIDE");
            colSv.setText("S.VIDE");
            tableMatrice.getColumns().add(colSv);

        } else {
            // Configuration Carrefour et toute nouvelle catégorie ajoutée !
            // Groupement intelligent par famille / nom de produit
            Map<String, List<Produit>> groupMap = new LinkedHashMap<>();

            for (Produit p : currentProduits) {
                String groupName = "TYPE DE PAIN";
                String lib = p.getLibelle().toUpperCase().trim();

                if (p.getFamille() != null && !p.getFamille().getNom().isBlank()) {
                    groupName = p.getFamille().getNom().toUpperCase();
                } else if (lib.startsWith("PJ") || lib.contains("PAIN JAUNE")) {
                    groupName = "PJ";
                } else if (lib.contains("S.VIDE") || lib.contains("SAC")) {
                    groupName = "S.VIDE";
                } else if (lib.contains("NUIT")) {
                    groupName = "TYPE DE PAIN NUIT";
                } else if (lib.contains("JOUR")) {
                    groupName = "TYPE DE PAIN JOUR";
                } else if (lib.contains("PATIS")) {
                    groupName = "PATISSERIE";
                }
                groupMap.computeIfAbsent(groupName, k -> new ArrayList<>()).add(p);
            }

            // Pour chaque groupe, créer l'en-tête parent de catégorie et les sous-colonnes
            for (Map.Entry<String, List<Produit>> entry : groupMap.entrySet()) {
                String groupTitle = entry.getKey();
                List<Produit> prods = entry.getValue();

                if ("S.VIDE".equalsIgnoreCase(groupTitle) && prods.size() == 1) {
                    TableColumn<MatriceRowData, Integer> col = creerColonneProduitObjet(prods.get(0));
                    col.setText("S.VIDE");
                    tableMatrice.getColumns().add(col);
                } else {
                    TableColumn<MatriceRowData, String> grp = createHeaderGroup(groupTitle);
                    // Trier les produits par prix
                    prods.sort(Comparator.comparing(p -> p.getPrixUnitaire() != null ? p.getPrixUnitaire() : BigDecimal.ZERO));
                    for (Produit p : prods) {
                        grp.getColumns().add(creerColonneProduitObjet(p));
                    }
                    tableMatrice.getColumns().add(grp);
                }
            }
        }

        // 3. FACTURE (Calculé dynamiquement en temps réel avec bordure noire)
        TableColumn<MatriceRowData, String> colFacture = new TableColumn<>("FACTURE");
        colFacture.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().facture)));
        colFacture.setCellFactory(col -> createStyledCell("-fx-background-color: #E8F5E9; -fx-text-fill: #1B5E20; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;"));
        colFacture.setPrefWidth(105);

        // 4. ECART PRECEDENT (Dette veille)
        TableColumn<MatriceRowData, String> colEcartPrec = new TableColumn<>("ECART\nPRECEDENT");
        colEcartPrec.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().ecartPrecedent)));
        colEcartPrec.setCellFactory(col -> createStyledCell("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-alignment: CENTER-RIGHT;"));
        colEcartPrec.setPrefWidth(105);

        // 5. MANQUANT (Éditable inline)
        TableColumn<MatriceRowData, BigDecimal> colManquant = new TableColumn<>("MANQUANT");
        colManquant.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().manquant));
        colManquant.setCellFactory(col -> new TableCell<>() {
            private final TextField txt = new TextField();
            {
                txt.setAlignment(Pos.CENTER_RIGHT);
                txt.setStyle("-fx-background-color: transparent; -fx-font-size: 12px; -fx-padding: 2;");
                txt.focusedProperty().addListener((obs, ov, nv) -> {
                    if (nv) {
                        txt.setStyle("-fx-background-color: white; -fx-border-color: #D93025; -fx-border-width: 1px; -fx-padding: 2;");
                        txt.selectAll();
                    } else {
                        txt.setStyle("-fx-background-color: transparent; -fx-font-size: 12px; -fx-padding: 2;");
                        validerEtEnregistrerManquant(getIndex(), txt.getText());
                    }
                });
                txt.setOnAction(e -> validerEtEnregistrerManquant(getIndex(), txt.getText()));
            }
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(borderStyle);
                if (empty || getIndex() >= dataMatrice.size()) {
                    setGraphic(null);
                } else {
                    txt.setText(item != null && item.compareTo(BigDecimal.ZERO) > 0 ? item.toPlainString() : "");
                    txt.setPromptText("—");
                    setGraphic(txt);
                }
            }
        });
        colManquant.setPrefWidth(90);

        // 6. TOTAL SOLDE = FACTURE + ECART PRECEDENT + MANQUANT
        TableColumn<MatriceRowData, String> colTotalSolde = new TableColumn<>("TOTAL\nSOLDE");
        colTotalSolde.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().totalSolde)));
        colTotalSolde.setCellFactory(col -> createStyledCell("-fx-background-color: #F0F4F8; -fx-text-fill: #0D47A1; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;"));
        colTotalSolde.setPrefWidth(110);

        // 7. VERSEMENT (Éditable inline)
        TableColumn<MatriceRowData, BigDecimal> colVers = new TableColumn<>("VERSEMENT");
        colVers.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().versementJour));
        colVers.setCellFactory(col -> new TableCell<>() {
            private final TextField txtVers = new TextField();
            {
                txtVers.setAlignment(Pos.CENTER_RIGHT);
                txtVers.setStyle("-fx-background-color: transparent; -fx-text-fill: #1565C0; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 2;");
                txtVers.focusedProperty().addListener((obs, ov, nv) -> {
                    if (nv) {
                        txtVers.setStyle("-fx-background-color: white; -fx-border-color: #1565C0; -fx-border-width: 1px; -fx-text-fill: #1565C0; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 2;");
                        txtVers.selectAll();
                    } else {
                        txtVers.setStyle("-fx-background-color: transparent; -fx-text-fill: #1565C0; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 2;");
                        validerEtEnregistrerVersement(getIndex(), txtVers.getText());
                    }
                });
                txtVers.setOnAction(e -> validerEtEnregistrerVersement(getIndex(), txtVers.getText()));
            }
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(borderStyle);
                if (empty || getIndex() >= dataMatrice.size()) {
                    setGraphic(null);
                } else {
                    txtVers.setText(item != null && item.compareTo(BigDecimal.ZERO) > 0 ? item.toPlainString() : "");
                    txtVers.setPromptText("0");
                    setGraphic(txtVers);
                }
            }
        });
        colVers.setPrefWidth(105);

        // 8. VERS. VEILLE
        TableColumn<MatriceRowData, String> colVersVeille = new TableColumn<>("VERS.\nVEILLE");
        colVersVeille.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().versementVeille)));
        colVersVeille.setCellFactory(col -> createStyledCell("-fx-background-color: #FFFFFF; -fx-text-fill: #5F6368; -fx-alignment: CENTER-RIGHT;"));
        colVersVeille.setPrefWidth(95);

        // 9. VISA
        TableColumn<MatriceRowData, String> colVisa = new TableColumn<>("VISA");
        colVisa.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().visa ? "✔" : "—"));
        colVisa.setCellFactory(col -> createStyledCell("-fx-background-color: #FFFFFF; -fx-text-fill: #1B5E20; -fx-font-weight: bold; -fx-alignment: CENTER;"));
        colVisa.setPrefWidth(55);

        // 10. ECART VERSEMENT (Dette finale de fin de journée)
        TableColumn<MatriceRowData, String> colEcartVers = new TableColumn<>("ECART\nVERSEMENT");
        colEcartVers.setCellValueFactory(d -> new SimpleStringProperty(FormatUtil.montant(d.getValue().ecartVersement)));
        colEcartVers.setCellFactory(col -> createStyledCell("-fx-background-color: #FFEBEE; -fx-text-fill: #B71C1C; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;"));
        colEcartVers.setPrefWidth(115);

        // Action retirer rapide
        TableColumn<MatriceRowData, Void> colActions = new TableColumn<>("");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRetirer = new Button("✕");
            {
                btnRetirer.setStyle("-fx-background-color: transparent; -fx-text-fill: #9CA3AF; -fx-font-weight: bold; -fx-cursor: hand;");
                btnRetirer.setTooltip(new Tooltip("Effacer cette ligne"));
                btnRetirer.setOnAction(e -> retirerLigneClient(getIndex()));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(borderStyle);
                setGraphic(empty ? null : btnRetirer);
            }
        });
        colActions.setPrefWidth(35);

        tableMatrice.getColumns().addAll(colFacture, colEcartPrec, colManquant, colTotalSolde, colVers, colVersVeille, colVisa, colEcartVers, colActions);
    }

    private TableColumn<MatriceRowData, String> createHeaderGroup(String text) {
        TableColumn<MatriceRowData, String> grp = new TableColumn<>(text);
        grp.setStyle("-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-color: #FFFFFF; -fx-text-fill: #000000;");
        return grp;
    }

    private TableCell<MatriceRowData, String> createStyledCell(String specificStyle) {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-border-color: #000000; -fx-border-width: 0.5px;");
                } else {
                    setText(item);
                    setStyle(specificStyle + " -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 3 6;");
                }
            }
        };
    }

    private TableColumn<MatriceRowData, Integer> creerColonneProduitDynamique(String pName) {
        Produit p = trouverOuCreerProduit(pName);
        return creerColonneProduitObjet(p);
    }

    private TableColumn<MatriceRowData, Integer> creerColonneProduitObjet(Produit p) {
        String colName = p.getLibelle().toUpperCase().replace("PAIN", "").replace("PJ", "").trim();
        if (colName.isBlank() && p.getPrixUnitaire() != null) colName = String.valueOf(p.getPrixUnitaire().intValue());

        TableColumn<MatriceRowData, Integer> colP = new TableColumn<>(colName);
        colP.setStyle("-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-color: #FFFFFF;");
        colP.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getQuantiteForProduit(p.getId())));

        colP.setCellFactory(col -> new TableCell<>() {
            private final TextField txtField = new TextField();
            {
                txtField.setAlignment(Pos.CENTER);
                txtField.setStyle("-fx-background-color: transparent; -fx-padding: 2; -fx-font-weight: bold; -fx-font-size: 12px;");
                txtField.focusedProperty().addListener((obs, ov, nv) -> {
                    if (nv) {
                        txtField.setStyle("-fx-background-color: white; -fx-border-color: #1A73E8; -fx-border-width: 1.5px; -fx-padding: 2;");
                        txtField.selectAll();
                    } else {
                        txtField.setStyle("-fx-background-color: transparent; -fx-padding: 2; -fx-font-weight: bold; -fx-font-size: 12px;");
                        validerEtEnregistrerQte(getIndex(), p, txtField.getText());
                    }
                });
                txtField.setOnAction(e -> validerEtEnregistrerQte(getIndex(), p, txtField.getText()));
            }

            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-border-color: #000000; -fx-border-width: 0.5px; -fx-alignment: CENTER;");
                if (empty || getIndex() >= dataMatrice.size()) {
                    setGraphic(null);
                } else {
                    txtField.setText(item != null && item > 0 ? String.valueOf(item) : "");
                    txtField.setPromptText("—");
                    setGraphic(txtField);
                }
            }
        });

        colP.setPrefWidth(62);
        return colP;
    }

    private Produit trouverOuCreerProduit(String pName) {
        String clean = pName.trim().toUpperCase();
        for (Produit p : currentProduits) {
            if (p.getLibelle().toUpperCase().trim().equals(clean)) return p;
            if (p.getCode().toUpperCase().contains(clean)) return p;
        }

        try {
            BigDecimal val = new BigDecimal(clean.replace("GL", "").replace("GC", "").trim());
            for (Produit p : currentProduits) {
                if (p.getPrixUnitaire().compareTo(val) == 0) return p;
            }
        } catch (Exception ignored) {}

        if (!currentProduits.isEmpty()) return currentProduits.get(0);
        Produit dummy = new Produit();
        dummy.setId("PROD-" + clean);
        dummy.setLibelle(clean);
        dummy.setPrixUnitaire(new BigDecimal(40));
        return dummy;
    }

    private void ajouterClientALaMatrice(Client client) {
        for (MatriceRowData r : dataMatrice) {
            if (r.client.getId().equals(client.getId())) {
                setSyncStatus("ℹ️ Client déjà présent dans la feuille", "#1A73E8");
                return;
            }
        }

        BigDecimal soldeActuel = client.getSoldeActuel() != null ? client.getSoldeActuel() : BigDecimal.ZERO;
        BigDecimal vJour = versementsJour.getOrDefault(client.getId(), BigDecimal.ZERO);
        BigDecimal vVeille = versementsVeille.getOrDefault(client.getId(), BigDecimal.ZERO);

        MatriceRowData newRow = new MatriceRowData(
            client,
            new ArrayList<>(),
            BigDecimal.ZERO,
            soldeActuel,
            BigDecimal.ZERO,
            soldeActuel,
            vJour,
            vVeille,
            soldeActuel.subtract(vJour)
        );

        dataMatrice.add(0, newRow);
        recalculerTotauxFooter();
        setSyncStatus("✅ Ligne ajoutée pour " + client.getNom() + " (prête à la saisie)", "#2E7D32");
    }

    // ── Modification en direct sans aucun popup ──────────────────
    private void validerEtEnregistrerQte(int rowIndex, Produit produit, String texte) {
        if (rowIndex < 0 || rowIndex >= dataMatrice.size()) return;
        MatriceRowData row = dataMatrice.get(rowIndex);

        int qte = 0;
        try {
            if (texte != null && !texte.trim().isEmpty()) {
                qte = Math.max(0, Integer.parseInt(texte.trim()));
            }
        } catch (Exception ignored) {
            return;
        }

        int qteActuelle = row.getQuantiteForProduit(produit.getId());
        if (qte == qteActuelle) return;

        setSyncStatus("💾 Calcul & sauvegarde...", "#E37400");
        final int qteFinale = qte;

        POOL.submit(() -> {
            try {
                LigneCommande ligne = row.findLigneForProduit(produit.getId());
                if (ligne == null && qteFinale > 0) {
                    ligne = new LigneCommande();
                    ligne.setFicheId(currentFiche.getId());
                    ligne.setClient(row.client);
                    ligne.setProduit(produit);
                    ligne.setQuantiteSortie(qteFinale);
                    ligne.setQuantiteRetournee(0);
                    BigDecimal prix = tarifDAO.findPrixSpecifique(row.client.getId(), produit.getId(), currentFiche.getDateFiche())
                        .orElse(produit.getPrixUnitaire());
                    ligne.setTarifApplicable(prix);
                    ligne.setMontantHt(prix.multiply(BigDecimal.valueOf(qteFinale)));
                    ficheDAO.saveLigne(ligne);
                    row.lignes.add(ligne);
                } else if (ligne != null) {
                    if (qteFinale > 0) {
                        ligne.setQuantiteSortie(qteFinale);
                        BigDecimal prix = ligne.getPrixUnitaire();
                        ligne.setMontantHt(prix.multiply(BigDecimal.valueOf(qteFinale)));
                        ficheDAO.updateLigne(ligne);
                    } else {
                        ficheDAO.deleteLigne(ligne.getId());
                        row.lignes.remove(ligne);
                    }
                }

                ficheDAO.recalculerTotauxFiche(currentFiche.getId());

                Platform.runLater(() -> {
                    row.recalculer();
                    tableMatrice.refresh();
                    recalculerTotauxFooter();
                    setSyncStatus("🟢 Données synchronisées", "#2E7D32");
                });

            } catch (Exception ex) {
                Platform.runLater(() -> setSyncStatus("❌ Erreur : " + ex.getMessage(), "#C62828"));
            }
        });
    }

    private void validerEtEnregistrerVersement(int rowIndex, String texte) {
        if (rowIndex < 0 || rowIndex >= dataMatrice.size()) return;
        MatriceRowData row = dataMatrice.get(rowIndex);

        BigDecimal montant = BigDecimal.ZERO;
        try {
            if (texte != null && !texte.trim().isEmpty()) {
                montant = new BigDecimal(texte.trim().replace(" ", "").replace(",", "."));
            }
        } catch (Exception ignored) {
            return;
        }

        if (montant.compareTo(row.versementJour) == 0) return;

        setSyncStatus("💾 Sauvegarde versement...", "#E37400");
        final BigDecimal montantFinal = montant;

        POOL.submit(() -> {
            try {
                versementDAO.enregistrerOuMettreAJourVersement(
                    currentFiche.getId(),
                    row.client.getId(),
                    currentFiche.getDateFiche(),
                    montantFinal,
                    session.getUserId()
                );

                versementsJour.put(row.client.getId(), montantFinal);

                Platform.runLater(() -> {
                    row.versementJour = montantFinal;
                    row.recalculer();
                    tableMatrice.refresh();
                    recalculerTotauxFooter();
                    setSyncStatus("🟢 Versement enregistré (" + FormatUtil.montant(montantFinal) + " F)", "#2E7D32");
                });

            } catch (Exception ex) {
                Platform.runLater(() -> setSyncStatus("❌ Erreur : " + ex.getMessage(), "#C62828"));
            }
        });
    }

    private void validerEtEnregistrerManquant(int rowIndex, String texte) {
        if (rowIndex < 0 || rowIndex >= dataMatrice.size()) return;
        MatriceRowData row = dataMatrice.get(rowIndex);

        BigDecimal montant = BigDecimal.ZERO;
        try {
            if (texte != null && !texte.trim().isEmpty()) {
                montant = new BigDecimal(texte.trim().replace(" ", "").replace(",", "."));
            }
        } catch (Exception ignored) {
            return;
        }

        if (montant.compareTo(row.manquant) == 0) return;

        row.manquant = montant;
        row.recalculer();
        tableMatrice.refresh();
        recalculerTotauxFooter();
    }

    private void retirerLigneClient(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= dataMatrice.size()) return;
        MatriceRowData row = dataMatrice.get(rowIndex);

        POOL.submit(() -> {
            try {
                for (LigneCommande l : new ArrayList<>(row.lignes)) {
                    ficheDAO.deleteLigne(l.getId());
                }
                ficheDAO.recalculerTotauxFiche(currentFiche.getId());

                Platform.runLater(() -> {
                    dataMatrice.remove(rowIndex);
                    recalculerTotauxFooter();
                    setSyncStatus("🗑️ Ligne retirée pour " + row.client.getNom(), "#5F6368");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setSyncStatus("❌ Erreur : " + ex.getMessage(), "#C62828"));
            }
        });
    }

    // ── Synthèse des Totaux en bas (comme Row TOTAL3 sur Excel) ──
    private void recalculerTotauxFooter() {
        int totLignes = dataMatrice.size();
        int totPcs = 0;
        BigDecimal totFacture = BigDecimal.ZERO;
        BigDecimal totEcartPrec = BigDecimal.ZERO;
        BigDecimal totManquant = BigDecimal.ZERO;
        BigDecimal totSolde = BigDecimal.ZERO;
        BigDecimal totVers = BigDecimal.ZERO;
        BigDecimal totVersVeille = BigDecimal.ZERO;
        BigDecimal totEcartVers = BigDecimal.ZERO;

        for (MatriceRowData r : dataMatrice) {
            for (LigneCommande l : r.lignes) {
                totPcs += l.getQuantiteSortie();
            }
            totFacture = totFacture.add(r.facture);
            totEcartPrec = totEcartPrec.add(r.ecartPrecedent);
            totManquant = totManquant.add(r.manquant);
            totSolde = totSolde.add(r.totalSolde);
            totVers = totVers.add(r.versementJour);
            totVersVeille = totVersVeille.add(r.versementVeille);
            totEcartVers = totEcartVers.add(r.ecartVersement);
        }

        lblTotalsFooter.setText("TOTAL : " + totLignes + " clients | FACTURE : " + FormatUtil.montant(totFacture)
            + " F | ÉCART PRÉC. : " + FormatUtil.montant(totEcartPrec)
            + " F | MANQUANT : " + FormatUtil.montant(totManquant)
            + " F | TOTAL SOLDE : " + FormatUtil.montant(totSolde)
            + " F | VERSEMENT : " + FormatUtil.montant(totVers)
            + " F | VERS. VEILLE : " + FormatUtil.montant(totVersVeille)
            + " F | ÉCART FINAL (DETTE) : " + FormatUtil.montant(totEcartVers) + " FCFA");

        lblTotalSorties.setText("Total Pains Sortis\n" + totPcs + " pièces");
        lblTotalNet.setText("Total Facture Net\n" + FormatUtil.montant(totFacture) + " FCFA");
        lblTotalVersements.setText("Versements Reçus\n" + FormatUtil.montant(totVers) + " FCFA");
        lblTotalDette.setText("Reste Net à Encaisser\n" + FormatUtil.montant(totEcartVers) + " FCFA");
    }

    private void setSyncStatus(String text, String color) {
        lblSyncStatus.setText(text);
        lblSyncStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    @Override
    public void refresh() {
        if (dpDateFiche != null && dpDateFiche.getValue() != null) {
            chargerOuCreerFiche(dpDateFiche.getValue());
        }
    }

    private void exporterPDF() {
        if (currentFiche == null || currentLignes.isEmpty()) {
            setSyncStatus("ℹ️ Aucune donnée à exporter", "#5F6368");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter Fiche Journalière PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF (*.pdf)", "*.pdf"));
        fc.setInitialFileName("FICHE_SORTIE_" + currentFiche.getDateFiche() + ".pdf");
        File file = fc.showSaveDialog(mainWindow.getStage());
        if (file == null) return;

        runAsync(() -> {
            PdfService.exporterFicheSortie(currentFiche.getDateFiche(), currentLignes, file.getAbsolutePath());
            return true;
        }, ok -> setSyncStatus("✅ Export PDF réussi : " + file.getName(), "#2E7D32"));
    }

    private void exporterExcel() {
        if (currentFiche == null || currentLignes.isEmpty()) {
            setSyncStatus("ℹ️ Aucune donnée à exporter", "#5F6368");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter Fiche Journalière Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier CSV / Excel (*.csv)", "*.csv"));
        fc.setInitialFileName("FICHE_SORTIE_" + currentFiche.getDateFiche() + ".csv");
        File file = fc.showSaveDialog(mainWindow.getStage());
        if (file == null) return;

        runAsync(() -> {
            ExcelExportService.exporterFicheSortieExcel(currentFiche.getDateFiche(), currentLignes, file);
            return true;
        }, ok -> setSyncStatus("✅ Export Excel réussi : " + file.getName(), "#2E7D32"));
    }

    // ── Structure de données pour une ligne de la matrice ────────
    public static class MatriceRowData {
        public final Client client;
        public final List<LigneCommande> lignes;
        public BigDecimal facture;
        public BigDecimal ecartPrecedent;
        public BigDecimal manquant;
        public BigDecimal totalSolde;
        public BigDecimal versementJour;
        public BigDecimal versementVeille;
        public BigDecimal ecartVersement;
        public boolean visa = false;

        public MatriceRowData(Client client, List<LigneCommande> lignes, BigDecimal facture,
                              BigDecimal ecartPrecedent, BigDecimal manquant, BigDecimal totalSolde,
                              BigDecimal versementJour, BigDecimal versementVeille, BigDecimal ecartVersement) {
            this.client = client;
            this.lignes = new ArrayList<>(lignes);
            this.facture = facture != null ? facture : BigDecimal.ZERO;
            this.ecartPrecedent = ecartPrecedent != null ? ecartPrecedent : BigDecimal.ZERO;
            this.manquant = manquant != null ? manquant : BigDecimal.ZERO;
            this.totalSolde = totalSolde != null ? totalSolde : BigDecimal.ZERO;
            this.versementJour = versementJour != null ? versementJour : BigDecimal.ZERO;
            this.versementVeille = versementVeille != null ? versementVeille : BigDecimal.ZERO;
            this.ecartVersement = ecartVersement != null ? ecartVersement : BigDecimal.ZERO;
        }

        public int getQuantiteForProduit(String produitId) {
            if (lignes == null || produitId == null) return 0;
            return lignes.stream()
                .filter(l -> l.getProduit() != null && produitId.equals(l.getProduit().getId()))
                .mapToInt(LigneCommande::getQuantiteNette)
                .sum();
        }

        public LigneCommande findLigneForProduit(String produitId) {
            if (lignes == null || produitId == null) return null;
            return lignes.stream()
                .filter(l -> l.getProduit() != null && produitId.equals(l.getProduit().getId()))
                .findFirst()
                .orElse(null);
        }

        public void recalculer() {
            BigDecimal total = BigDecimal.ZERO;
            for (LigneCommande l : lignes) {
                if (l.getMontantHt() != null && l.getMontantHt().compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(l.getMontantHt());
                } else if (l.getQuantiteSortie() > 0) {
                    BigDecimal pu = l.getPrixUnitaire();
                    total = total.add(pu.multiply(BigDecimal.valueOf(l.getQuantiteSortie())));
                }
            }
            this.facture = total;
            this.totalSolde = this.facture.add(this.ecartPrecedent).add(this.manquant);
            this.ecartVersement = this.totalSolde.subtract(this.versementJour);
        }
    }
}
