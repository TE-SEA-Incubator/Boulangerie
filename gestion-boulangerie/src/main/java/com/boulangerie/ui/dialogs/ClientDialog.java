package com.boulangerie.ui.dialogs;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.DeblocageService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.ui.components.StatusBadge;
import com.boulangerie.ui.components.StyledTable;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Fiche client complète — écran n°6 de la Planche 1.
 * Onglets : Fiche | Historique (Sorties, Factures, Versements)
 */
public class ClientDialog extends JDialog {

    private final ClientDAO        clientDAO    = new ClientDAO();
    private final UtilisateurDAO   userDAO      = new UtilisateurDAO();
    private final FactureDAO       factureDAO   = new FactureDAO();
    private final VersementDAO     versementDAO = new VersementDAO();
    private final FicheJournaliereDAO ficheDAO  = new FicheJournaliereDAO();
    private final AuditDAO         auditDAO     = new AuditDAO();
    private final DeblocageService deblocageService = new DeblocageService();
    private final SessionService   session      = SessionService.getInstance();
    private boolean saved = false;

    private final Client client;

    // Champs fiche
    private JTextField txtCode, txtNom, txtQuartier, txtTelephone, txtEmail;
    private JTextField txtDelai, txtPlafond;
    private JComboBox<CategorieClient> cboCategorie;
    private JComboBox<String>          cboType, cboStatut;
    private JComboBox<Utilisateur>     cboLivreur;
    private JLabel lblSoldePrecedent, lblSoldeActuel;

    // Onglet historique
    private DefaultTableModel modelSorties, modelFactures, modelVersements;

    public ClientDialog(Frame parent, Client client) {
        super(parent,
            client == null ? "Nouveau Client" : "Fiche Client — " + client.getCode(),
            true);
        this.client = client != null ? client : new Client();
        setSize(920, 660);
        setLocationRelativeTo(parent);
        buildUI();
        if (client != null) {
            remplir();
            chargerHistorique();
        }
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(UIConstants.GRIS_FOND);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UIConstants.FONT_NORMAL);
        tabs.addTab("Fiche",        buildFichePanel());
        tabs.addTab("Historique",   buildHistoriquePanel());
        tabs.addTab("Informations complémentaires", buildInfosCompPanel());

        // ── Footer ────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        footer.setBackground(UIConstants.GRIS_FOND);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.GRIS_BORDURE));

        RoundedButton btnSave   = new RoundedButton("Enregistrer",         RoundedButton.Style.PRIMARY);
        RoundedButton btnCancel = new RoundedButton("Annuler",              RoundedButton.Style.SECONDARY);
        btnSave.addActionListener(e   -> sauvegarder());
        btnCancel.addActionListener(e -> dispose());
        footer.add(btnSave); footer.add(btnCancel);

        if (session.hasPermission("DEBLOCAGE_WRITE") && client != null && client.getId() != null) {
            RoundedButton btnBlocage = new RoundedButton(
                client.isBloque() ? "Débloquer ce client" : "Bloquer ce client",
                client.isBloque() ? RoundedButton.Style.SUCCESS : RoundedButton.Style.DANGER);
            btnBlocage.addActionListener(e -> toggleBlocage());
            footer.add(btnBlocage);
        }

        add(tabs,   BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    // ── Onglet Fiche ──────────────────────────────────────────────
    private JPanel buildFichePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        // Colonne gauche
        txtCode      = addLabelField(p, gc, "Identifiant *",        0, 0);
        txtNom       = addLabelField(p, gc, "Nom du client *",       1, 0);
        txtQuartier  = addLabelField(p, gc, "Quartier / Ville",      2, 0);
        txtTelephone = addLabelField(p, gc, "Téléphone",             3, 0);
        txtEmail     = addLabelField(p, gc, "Email",                 4, 0);

        addLabel(p, gc, "Catégorie *", 5, 0);
        gc.gridx = 1; gc.gridy = 5; gc.weightx = 1;
        cboCategorie = new JComboBox<>();
        clientDAO.findAllCategories().forEach(cboCategorie::addItem);
        p.add(cboCategorie, gc); gc.weightx = 0;

        addLabel(p, gc, "Livreur rattaché", 6, 0);
        gc.gridx = 1; gc.gridy = 6; gc.weightx = 1;
        cboLivreur = new JComboBox<>();
        Utilisateur vide = new Utilisateur(); vide.setNomComplet("— Aucun —");
        cboLivreur.addItem(vide);
        userDAO.findLivreurs().forEach(cboLivreur::addItem);
        p.add(cboLivreur, gc); gc.weightx = 0;

        // Colonne droite
        addLabel(p, gc, "Type *", 0, 2);
        gc.gridx = 3; gc.gridy = 0; gc.weightx = 1;
        cboType = new JComboBox<>(new String[]{"Nominatif", "Anonyme"});
        p.add(cboType, gc); gc.weightx = 0;

        addLabel(p, gc, "Délai paiement (jours)", 1, 2);
        gc.gridx = 3; gc.gridy = 1; gc.weightx = 1;
        txtDelai = new JTextField("30"); p.add(txtDelai, gc); gc.weightx = 0;

        addLabel(p, gc, "Plafond de crédit (FCFA)", 2, 2);
        gc.gridx = 3; gc.gridy = 2; gc.weightx = 1;
        txtPlafond = new JTextField("0"); p.add(txtPlafond, gc); gc.weightx = 0;

        addLabel(p, gc, "Solde précédent (FCFA)", 3, 2);
        gc.gridx = 3; gc.gridy = 3;
        lblSoldePrecedent = new JLabel("0,00");
        lblSoldePrecedent.setFont(UIConstants.FONT_NORMAL);
        p.add(lblSoldePrecedent, gc);

        addLabel(p, gc, "Solde actuel (FCFA)", 4, 2);
        gc.gridx = 3; gc.gridy = 4;
        lblSoldeActuel = new JLabel("0,00");
        lblSoldeActuel.setFont(UIConstants.FONT_BOLD);
        lblSoldeActuel.setForeground(UIConstants.ROUGE_DANGER);
        p.add(lblSoldeActuel, gc);

        addLabel(p, gc, "Statut", 5, 2);
        gc.gridx = 3; gc.gridy = 5; gc.weightx = 1;
        cboStatut = new JComboBox<>(new String[]{"Actif", "Bloqué", "Inactif"});
        p.add(cboStatut, gc); gc.weightx = 0;

        return p;
    }

    // ── Onglet Historique ─────────────────────────────────────────
    private JPanel buildHistoriquePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JTabbedPane inner = new JTabbedPane();
        inner.setFont(UIConstants.FONT_NORMAL);

        // Sorties
        modelSorties = new DefaultTableModel(
            new String[]{"N° document","Date","Type","Montant (FCFA)","Statut","Livreur","Observations"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable tableSorties = new StyledTable(modelSorties);
        tableSorties.getColumnModel().getColumn(4).setCellRenderer(
            (t, v, s, f, r, c) -> StatusBadge.forStatut(v != null ? v.toString() : ""));
        inner.addTab("Sorties / Retours", new JScrollPane(tableSorties));

        // Factures
        modelFactures = new DefaultTableModel(
            new String[]{"N° Facture","Date","Montant HT","TVA","TTC","Statut","Livreur"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable tableFactures = new StyledTable(modelFactures);
        tableFactures.getColumnModel().getColumn(5).setCellRenderer(
            (t, v, s, f, r, c) -> StatusBadge.forStatut(v != null ? v.toString() : ""));
        inner.addTab("Factures", new JScrollPane(tableFactures));

        // Versements
        modelVersements = new DefaultTableModel(
            new String[]{"N° Reçu","Date","Attendu","Remis","Enregistré","Écart","Statut","Mode"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable tableVers = new StyledTable(modelVersements);
        tableVers.getColumnModel().getColumn(6).setCellRenderer(
            (t, v, s, f, r, c) -> StatusBadge.forStatut(v != null ? v.toString() : ""));
        inner.addTab("Versements", new JScrollPane(tableVers));

        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    // ── Onglet Informations complémentaires ───────────────────────
    private JPanel buildInfosCompPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        // Tarifs spécifiques
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        JLabel lblTarifs = new JLabel("Tarifs spécifiques client");
        lblTarifs.setFont(UIConstants.FONT_BOLD);
        p.add(lblTarifs, gc);

        gc.gridy = 1;
        DefaultTableModel tarifModel = new DefaultTableModel(
            new String[]{"Produit","Prix spécifique","Du","Au","Actif"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable tarifTable = new StyledTable(tarifModel);
        tarifTable.setPreferredScrollableViewportSize(new Dimension(600, 120));

        if (client.getId() != null) {
            TarifClientDAO tcDAO = new TarifClientDAO();
            tcDAO.findByClient(client.getId()).forEach(row -> {
                tarifModel.addRow(new Object[]{
                    row.get("produit_libelle"),
                    FormatUtil.montant((BigDecimal) row.get("prix")),
                    row.get("date_debut") != null ? FormatUtil.date((java.time.LocalDate) row.get("date_debut")) : "—",
                    row.get("date_fin")   != null ? FormatUtil.date((java.time.LocalDate) row.get("date_fin"))   : "—",
                    Boolean.TRUE.equals(row.get("actif")) ? "✓" : "✗"
                });
            });
        }
        p.add(new JScrollPane(tarifTable), gc);

        // Autorisations de déblocage
        gc.gridy = 2; gc.gridwidth = 2;
        JLabel lblAuth = new JLabel("Historique déblocages exceptionnels");
        lblAuth.setFont(UIConstants.FONT_BOLD);
        p.add(lblAuth, gc);

        gc.gridy = 3;
        DefaultTableModel authModel = new DefaultTableModel(
            new String[]{"Date","Manager","Motif","Montant autorisé","Validité"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        StyledTable authTable = new StyledTable(authModel);
        authTable.setPreferredScrollableViewportSize(new Dimension(600, 100));

        if (client.getId() != null) {
            AutorisationDeblocageDAO authDAO = new AutorisationDeblocageDAO();
            authDAO.findByClient(client.getId()).forEach(a -> {
                authModel.addRow(new Object[]{
                    FormatUtil.dateHeure(a.getDateAutorisation()),
                    a.getManagerId(),
                    a.getMotif(),
                    a.getMontantAutorise() != null ? FormatUtil.montant(a.getMontantAutorise()) : "—",
                    a.getDureeValidite() != null ? FormatUtil.date(a.getDureeValidite()) : "Illimité"
                });
            });
        }
        p.add(new JScrollPane(authTable), gc);

        return p;
    }

    // ── Remplir depuis modèle ─────────────────────────────────────
    private void remplir() {
        txtCode.setText(client.getCode() != null ? client.getCode() : "");
        txtNom.setText(client.getNom()   != null ? client.getNom()   : "");
        txtTelephone.setText(client.getTelephone() != null ? client.getTelephone() : "");
        txtEmail.setText(client.getEmail()         != null ? client.getEmail()     : "");
        String qv = (client.getQuartier() != null ? client.getQuartier() : "")
                  + (client.getVille() != null && !client.getVille().isBlank() ? " " + client.getVille() : "");
        txtQuartier.setText(qv.trim());
        txtDelai.setText(String.valueOf(client.getDelaiPaiement()));
        txtPlafond.setText(client.getPlafondCredit().toPlainString());
        lblSoldePrecedent.setText(FormatUtil.montant(client.getSoldePrecedent()) + " FCFA");
        lblSoldeActuel.setText(FormatUtil.montant(client.getSoldeActuel()) + " FCFA");
        lblSoldeActuel.setForeground(
            client.getSoldeActuel().compareTo(BigDecimal.ZERO) > 0
                ? UIConstants.ROUGE_DANGER : UIConstants.VERT_SUCCES);

        cboType.setSelectedItem(client.getTypeClient().name());
        cboStatut.setSelectedItem(client.getStatut().name());

        if (client.getCategorie() != null) {
            for (int i = 0; i < cboCategorie.getItemCount(); i++) {
                if (cboCategorie.getItemAt(i).getId().equals(client.getCategorie().getId())) {
                    cboCategorie.setSelectedIndex(i); break;
                }
            }
        }
        if (client.getLivreurRattache() != null) {
            for (int i = 0; i < cboLivreur.getItemCount(); i++) {
                Utilisateur u = cboLivreur.getItemAt(i);
                if (u.getId() != null && u.getId().equals(client.getLivreurRattache().getId())) {
                    cboLivreur.setSelectedIndex(i); break;
                }
            }
        }
    }

    // ── Charger historique depuis BDD ─────────────────────────────
    private void chargerHistorique() {
        if (client.getId() == null) return;

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                // Fiches journalières (sorties)
                List<FicheJournaliere> fiches = ficheDAO.findByFilters(null, null, null, null);
                for (FicheJournaliere fj : fiches) {
                    fj.getLignes().stream()
                        .filter(l -> l.getClient() != null && client.getId().equals(l.getClient().getId()))
                        .forEach(l -> modelSorties.addRow(new Object[]{
                            fj.getNumero(),
                            FormatUtil.date(fj.getDateFiche()),
                            l.getQuantiteRetournee() > 0 ? "Retour" : "Sortie",
                            FormatUtil.montant(l.getMontantHt()),
                            fj.getStatut().name(),
                            fj.getLivreur() != null ? fj.getLivreur().getNomComplet() : "—",
                            l.getMotifRetour() != null ? l.getMotifRetour() : "—"
                        }));
                }

                // Factures
                List<Facture> factures = factureDAO.findByFilters(null, null, client.getId(), null);
                for (Facture f : factures) {
                    modelFactures.addRow(new Object[]{
                        f.getNumero(),
                        FormatUtil.date(f.getDateEmission()),
                        FormatUtil.montant(f.getMontantHt()),
                        FormatUtil.montant(f.getTvaMontant()),
                        FormatUtil.montant(f.getMontantTtc()),
                        f.getStatut().name(),
                        f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—"
                    });
                }

                // Versements
                List<Versement> versements = versementDAO.findByDate(java.time.LocalDate.now());
                versements.stream()
                    .filter(v -> v.getClient() != null && client.getId().equals(v.getClient().getId()))
                    .forEach(v -> modelVersements.addRow(new Object[]{
                        v.getNumero(),
                        FormatUtil.date(v.getDateVersement()),
                        FormatUtil.montant(v.getMontantAttendu()),
                        FormatUtil.montant(v.getMontantRemis()),
                        FormatUtil.montant(v.getMontantEnregistre()),
                        FormatUtil.montant(v.getEcart()),
                        v.getStatut().name(),
                        v.getModePaiement() != null ? v.getModePaiement() : "—"
                    }));
                return null;
            }
        };
        w.execute();
    }

    // ── Sauvegarder ───────────────────────────────────────────────
    private void sauvegarder() {
        if (txtCode.getText().isBlank() || txtNom.getText().isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Identifiant et Nom sont obligatoires.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            client.setCode(txtCode.getText().trim());
            client.setNom(txtNom.getText().trim());
            String qv = txtQuartier.getText().trim();
            client.setQuartier(qv);
            client.setTelephone(txtTelephone.getText().trim());
            client.setEmail(txtEmail.getText().trim());
            client.setDelaiPaiement(Integer.parseInt(txtDelai.getText().trim()));
            client.setPlafondCredit(new BigDecimal(txtPlafond.getText().trim().replace(",", ".")));
            client.setTypeClient(Client.TypeClient.valueOf((String) cboType.getSelectedItem()));
            client.setEstAnonyme(Client.TypeClient.Anonyme.equals(client.getTypeClient()));

            String statutStr = (String) cboStatut.getSelectedItem();
            for (Client.Statut s : Client.Statut.values()) {
                if (s.name().equals(statutStr)) { client.setStatut(s); break; }
            }

            client.setCategorie((CategorieClient) cboCategorie.getSelectedItem());
            Utilisateur liv = (Utilisateur) cboLivreur.getSelectedItem();
            client.setLivreurRattache(liv != null && liv.getId() != null ? liv : null);

            if (client.getId() == null || client.getId().isBlank()) {
                String id = clientDAO.save(client);
                auditDAO.log(new JournalAudit("Client", id, JournalAudit.CREATE,
                    session.getUserId(), session.getLogin(),
                    "Nouveau client : " + client.getCode() + " — " + client.getNom()));
            } else {
                clientDAO.update(client);
                auditDAO.log(new JournalAudit("Client", client.getId(), JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(),
                    "Modification client : " + client.getCode()));
            }
            saved = true;
            dispose();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Délai ou plafond invalide (entrez des nombres).", "Erreur", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Blocage / Déblocage ───────────────────────────────────────
    private void toggleBlocage() {
        if (client.getId() == null) return;
        boolean estBloque = Client.Statut.Bloqué.equals(client.getStatut());
        if (estBloque) {
            // Déblocage exceptionnel via dialog dédié
            DeblocageDialog dlg = new DeblocageDialog((Frame) getOwner(), client);
            dlg.setVisible(true);
            if (dlg.isConfirmed()) { saved = true; dispose(); }
        } else {
            // Blocage manuel
            String motif = JOptionPane.showInputDialog(this, "Motif du blocage :");
            if (motif != null && !motif.isBlank()) {
                deblocageService.bloquerClient(client.getId(), motif, client.getSoldeActuel());
                saved = true;
                dispose();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private JTextField addLabelField(JPanel p, GridBagConstraints gc, String label, int row, int colSet) {
        addLabel(p, gc, label, row, colSet);
        int baseCol = colSet * 2;
        gc.gridx = baseCol + 1; gc.gridy = row; gc.weightx = 1;
        JTextField tf = new JTextField(18);
        p.add(tf, gc);
        gc.weightx = 0;
        return tf;
    }

    private void addLabel(JPanel p, GridBagConstraints gc, String label, int row, int colSet) {
        int baseCol = colSet * 2;
        gc.gridx = baseCol; gc.gridy = row; gc.weightx = 0;
        p.add(new JLabel(label), gc);
    }

    public boolean isSaved() { return saved; }
}
