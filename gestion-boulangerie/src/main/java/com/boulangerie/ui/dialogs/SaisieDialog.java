package com.boulangerie.ui.dialogs;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.*;
import com.boulangerie.ui.components.*;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dialogue de saisie des sorties/retours pour une fiche journalière.
 * Reproduit l'écran n°8 de la Planche 2.
 *
 * Règles CDC respectées :
 * - Résolution tarif : Spécifique > Catégorie > Standard (§8.2)
 * - Client bloqué → sortie refusée (sauf déblocage exceptionnel Manager)
 * - Quantité retournée ≤ Quantité sortie
 * - Motif retour obligatoire si quantité retournée > 0
 * - Remise exceptionnelle affichée séparément
 */
public class SaisieDialog extends JDialog {

    private final FicheJournaliere fiche;
    private final FicheJournaliereDAO ficheDAO    = new FicheJournaliereDAO();
    private final ClientDAO           clientDAO   = new ClientDAO();
    private final ProduitDAO          produitDAO  = new ProduitDAO();
    private final TarifService        tarifService = new TarifService();
    private final FacturationService  factService;
    private final SessionService      session     = SessionService.getInstance();

    private DefaultTableModel lignesModel;
    private StyledTable       lignesTable;

    // Totaux
    private JLabel lblNbClients, lblTotalSorties, lblTotalRetours, lblTotalNet;
    private JLabel lblStatut;

    // Saisie ligne
    private JComboBox<Client>  cboClient;
    private JComboBox<Produit> cboProduit;
    private JSpinner           spnSortie, spnRetour;
    private JLabel             lblTarifResolu, lblTypeTarif, lblRemiseInfo;
    private JTextField         txtRemisePct, txtMontant;
    private JComboBox<String>  cboMotifRetour;

    // Alertes
    private JLabel lblAlerteRetour, lblAlerteBlockage;

    public SaisieDialog(Frame parent, FicheJournaliere fiche, FacturationService factService) {
        super(parent, "Saisie Sorties / Retours — " + fiche.getNumero(), true);
        this.fiche       = fiche;
        this.factService = factService;
        setSize(1120, 700);
        setLocationRelativeTo(parent);
        buildUI();
        chargerLignesExistantes();
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(UIConstants.GRIS_FOND);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(UIConstants.GRIS_FOND);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        root.add(buildFicheHeader(),   BorderLayout.NORTH);
        root.add(buildCentrePanel(),   BorderLayout.CENTER);
        root.add(buildFooterPanel(),   BorderLayout.SOUTH);
        add(root);
    }

    // ── En-tête fiche ─────────────────────────────────────────────
    private JPanel buildFicheHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        addHeaderItem(p, "N° fiche",  fiche.getNumero());
        addHeaderItem(p, "Livreur",   fiche.getLivreur() != null ? fiche.getLivreur().getNomComplet() : "—");
        addHeaderItem(p, "Date",      FormatUtil.date(fiche.getDateFiche()));
        lblStatut = new JLabel("État : " + fiche.getStatut().name());
        lblStatut.setFont(UIConstants.FONT_BOLD);
        lblStatut.setForeground(UIConstants.BLEU_PRIMAIRE);
        p.add(lblStatut);
        return p;
    }

    private void addHeaderItem(JPanel p, String label, String value) {
        JLabel lbl = new JLabel(label + " : ");
        lbl.setFont(UIConstants.FONT_PETIT);
        lbl.setForeground(UIConstants.GRIS_TEXTE);
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BOLD);
        p.add(lbl); p.add(val);
    }

    // ── Zone centrale ──────────────────────────────────────────────
    private JPanel buildCentrePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(buildSaisiePanel(), BorderLayout.NORTH);
        p.add(buildTablePanel(),  BorderLayout.CENTER);
        p.add(buildBottomBar(),   BorderLayout.SOUTH);
        return p;
    }

    // ── Panneau de saisie d'une ligne ──────────────────────────────
    private JPanel buildSaisiePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Ajouter / Modifier une ligne"),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        // Ligne 1 : Client | Statut client | Produit | Famille
        gc.gridx = 0; gc.gridy = 0;
        p.add(new JLabel("Client"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        cboClient = new JComboBox<>();
        clientDAO.findAll().forEach(cboClient::addItem);
        cboClient.setPreferredSize(new Dimension(200, 30));
        p.add(cboClient, gc);
        cboClient.addActionListener(e -> { verifierBlocageClient(); recalculerTarif(); });

        gc.gridx = 2; gc.weightx = 0;
        p.add(new JLabel("Produit"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        cboProduit = new JComboBox<>();
        produitDAO.findAll(false).forEach(cboProduit::addItem);
        cboProduit.setPreferredSize(new Dimension(200, 30));
        p.add(cboProduit, gc);
        cboProduit.addActionListener(e -> recalculerTarif());

        // Ligne 2 : Qté sortie | Qté retournée | Tarif résolu | Type tarif
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        p.add(new JLabel("Qté sortie"), gc);
        gc.gridx = 1;
        spnSortie = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        p.add(spnSortie, gc);
        spnSortie.addChangeListener(e -> { validerQuantiteRetour(); recalculerMontant(); });

        gc.gridx = 2;
        p.add(new JLabel("Qté retournée"), gc);
        gc.gridx = 3;
        spnRetour = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        p.add(spnRetour, gc);
        spnRetour.addChangeListener(e -> { validerQuantiteRetour(); recalculerMontant(); });

        gc.gridx = 4;
        p.add(new JLabel("Tarif résolu (FCFA)"), gc);
        gc.gridx = 5;
        lblTarifResolu = new JLabel("—");
        lblTarifResolu.setFont(UIConstants.FONT_BOLD);
        p.add(lblTarifResolu, gc);

        gc.gridx = 6;
        p.add(new JLabel("Type"), gc);
        gc.gridx = 7;
        lblTypeTarif = new JLabel("—");
        lblTypeTarif.setFont(UIConstants.FONT_PETIT);
        lblTypeTarif.setForeground(UIConstants.BLEU_PRIMAIRE);
        p.add(lblTypeTarif, gc);

        // Ligne 3 : Remise | Montant | Motif retour
        gc.gridx = 0; gc.gridy = 2;
        p.add(new JLabel("Remise (%)"), gc);
        gc.gridx = 1;
        txtRemisePct = new JTextField("0");
        p.add(txtRemisePct, gc);
        txtRemisePct.addActionListener(e -> recalculerMontant());

        gc.gridx = 2;
        p.add(new JLabel("Montant HT"), gc);
        gc.gridx = 3;
        txtMontant = new JTextField("0,00");
        txtMontant.setEditable(false);
        txtMontant.setBackground(UIConstants.GRIS_FOND);
        txtMontant.setFont(UIConstants.FONT_BOLD);
        p.add(txtMontant, gc);

        gc.gridx = 4;
        p.add(new JLabel("Motif retour *"), gc);
        gc.gridx = 5; gc.gridwidth = 2;
        cboMotifRetour = new JComboBox<>(new String[]{
            "", "Produit abîmé", "Invendu", "Erreur quantité",
            "Mauvaise livraison", "Date dépassée", "Autre"
        });
        p.add(cboMotifRetour, gc);
        gc.gridwidth = 1;

        // Ligne 4 : info remise exceptionnelle + alertes
        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 4;
        lblRemiseInfo = new JLabel(" ");
        lblRemiseInfo.setFont(UIConstants.FONT_PETIT);
        lblRemiseInfo.setForeground(UIConstants.VERT_SUCCES);
        p.add(lblRemiseInfo, gc);

        gc.gridx = 4; gc.gridwidth = 4;
        JPanel alertes = new JPanel(new GridLayout(2, 1, 0, 2));
        alertes.setOpaque(false);
        lblAlerteRetour   = new JLabel(" ");
        lblAlerteBlockage = new JLabel(" ");
        lblAlerteRetour.setFont(UIConstants.FONT_PETIT);
        lblAlerteBlockage.setFont(UIConstants.FONT_PETIT);
        alertes.add(lblAlerteRetour);
        alertes.add(lblAlerteBlockage);
        p.add(alertes, gc);
        gc.gridwidth = 1;

        // Bouton Ajouter
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 8;
        RoundedButton btnAjouter = new RoundedButton("+ Ajouter la ligne", RoundedButton.Style.SUCCESS);
        btnAjouter.addActionListener(e -> ajouterLigne());
        p.add(btnAjouter, gc);

        return p;
    }

    // ── Table des lignes ───────────────────────────────────────────
    private JScrollPane buildTablePanel() {
        String[] cols = {"Client","Statut","Produit","Qté sortie","Qté retournée","Qté nette",
                         "Tarif","Type tarif","Remise (%)","Montant HT","Motif retour"};
        lignesModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        lignesTable = new StyledTable(lignesModel);
        lignesTable.getColumnModel().getColumn(1).setCellRenderer((t, v, s, f, r, c) ->
            StatusBadge.forStatut(v != null ? v.toString() : ""));
        // Bouton supprimer sur double-clic
        lignesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) proposerSuppression();
            }
        });
        JScrollPane scroll = new JScrollPane(lignesTable);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    // ── Barre du bas ───────────────────────────────────────────────
    private JPanel buildBottomBar() {
        JPanel p = new JPanel(new GridLayout(1, 4, 12, 0));
        p.setOpaque(false);

        lblNbClients    = buildTotalLabel("Nb clients :", "0",  UIConstants.NOIR_TEXTE);
        lblTotalSorties = buildTotalLabel("Total sorties :", "0,00", UIConstants.NOIR_TEXTE);
        lblTotalRetours = buildTotalLabel("Total retours :", "0,00", UIConstants.ORANGE_ALERTE);
        lblTotalNet     = buildTotalLabel("Total net :", "0,00",     UIConstants.VERT_SUCCES);

        p.add(lblNbClients); p.add(lblTotalSorties);
        p.add(lblTotalRetours); p.add(lblTotalNet);
        return p;
    }

    private JLabel buildTotalLabel(String label, String val, Color color) {
        JLabel lbl = new JLabel("<html><small>" + label + "</small><br><b>" + val + " FCFA</b></html>");
        lbl.setForeground(color);
        lbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        lbl.setBackground(Color.WHITE);
        lbl.setOpaque(true);
        return lbl;
    }

    // ── Pied de dialogue ───────────────────────────────────────────
    private JPanel buildFooterPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        RoundedButton btnBrouillon = new RoundedButton("Enregistrer brouillon", RoundedButton.Style.SECONDARY);
        RoundedButton btnFinaliser = new RoundedButton("✓ Finaliser",            RoundedButton.Style.SUCCESS);
        RoundedButton btnDeblocage = new RoundedButton("Débloquer client…",      RoundedButton.Style.DANGER);
        btnBrouillon.addActionListener(e -> enregistrerBrouillon());
        btnFinaliser.addActionListener(e -> finaliser());
        btnDeblocage.addActionListener(e -> ouvrirDeblocage());
        p.add(btnDeblocage);
        p.add(btnBrouillon);
        p.add(btnFinaliser);
        return p;
    }

    // ── Logique métier ────────────────────────────────────────────
    private void chargerLignesExistantes() {
        lignesModel.setRowCount(0);
        for (LigneSortie l : fiche.getLignes()) appendLigneDansTable(l);
        recalculerTotaux();
    }

    private void verifierBlocageClient() {
        Client cl = (Client) cboClient.getSelectedItem();
        if (cl != null && cl.isBloque()) {
            lblAlerteBlockage.setText("⛔ Client bloqué — sortie refusée (Manager requis pour débloquer)");
            lblAlerteBlockage.setForeground(UIConstants.ROUGE_DANGER);
        } else {
            lblAlerteBlockage.setText(" ");
        }
    }

    private void validerQuantiteRetour() {
        int sortie = (int) spnSortie.getValue();
        int retour = (int) spnRetour.getValue();
        if (retour > sortie) {
            lblAlerteRetour.setText("⚠ Qté retournée (" + retour + ") > Qté sortie (" + sortie + ") — invalide");
            lblAlerteRetour.setForeground(UIConstants.ROUGE_DANGER);
        } else {
            lblAlerteRetour.setText(" ");
        }
    }

    private void recalculerTarif() {
        Client  cl = (Client)  cboClient.getSelectedItem();
        Produit pr = (Produit) cboProduit.getSelectedItem();
        if (cl == null || pr == null || pr.getId() == null) return;

        try {
            TarifService.TarifResolu r = tarifService.resoudre(
                pr.getId(), cl, (int) spnSortie.getValue(), fiche.getDateFiche());

            lblTarifResolu.setText(FormatUtil.montant(r.prix()) + " FCFA");
            lblTypeTarif.setText(r.typeTarif());

            if (r.remiseMotif() != null && !r.remiseMotif().isBlank()) {
                lblRemiseInfo.setText("✓ Remise exceptionnelle active : " + r.remiseMotif()
                    + " — " + FormatUtil.montant(r.remisePct()) + "%");
                txtRemisePct.setText(r.remisePct().toPlainString());
            } else {
                lblRemiseInfo.setText(" ");
                txtRemisePct.setText("0");
            }
            recalculerMontant();
        } catch (Exception ex) {
            lblTarifResolu.setText("—");
        }
    }

    private void recalculerMontant() {
        try {
            String tarifStr = lblTarifResolu.getText().replace(" ","").replace(",",".").replace("FCFA","").trim();
            BigDecimal tarif  = new BigDecimal(tarifStr.isEmpty() ? "0" : tarifStr);
            int qSort = (int) spnSortie.getValue();
            int qRet  = (int) spnRetour.getValue();
            int qNet  = Math.max(0, qSort - qRet);
            BigDecimal brut = tarif.multiply(BigDecimal.valueOf(qNet));

            String remStr = txtRemisePct.getText().replace(",", ".");
            BigDecimal remPct = new BigDecimal(remStr.isEmpty() ? "0" : remStr);
            BigDecimal facteur = BigDecimal.ONE.subtract(
                remPct.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
            BigDecimal net = brut.multiply(facteur).setScale(2, java.math.RoundingMode.HALF_UP);
            txtMontant.setText(FormatUtil.montant(net));
        } catch (Exception ignore) {
            txtMontant.setText("0,00");
        }
    }

    private void ajouterLigne() {
        Client  cl = (Client)  cboClient.getSelectedItem();
        Produit pr = (Produit) cboProduit.getSelectedItem();
        if (cl == null || pr == null) return;

        // Vérifier blocage
        if (cl.isBloque()) {
            int r = JOptionPane.showOptionDialog(this,
                "Ce client est bloqué. Contacter le Manager pour un déblocage exceptionnel.",
                "Client bloqué", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[]{"Débloquer (Manager)…", "Annuler"}, "Annuler");
            if (r == 0) ouvrirDeblocage();
            return;
        }

        int qSort = (int) spnSortie.getValue();
        int qRet  = (int) spnRetour.getValue();

        // Validation quantités
        if (qSort <= 0) {
            JOptionPane.showMessageDialog(this, "La quantité sortie doit être > 0.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (qRet > qSort) {
            JOptionPane.showMessageDialog(this,
                "La quantité retournée (" + qRet + ") ne peut pas dépasser la quantité sortie (" + qSort + ").",
                "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Motif retour obligatoire si retour > 0
        if (qRet > 0) {
            String motif = (String) cboMotifRetour.getSelectedItem();
            if (motif == null || motif.isBlank()) {
                JOptionPane.showMessageDialog(this,
                    "Le motif de retour est obligatoire lorsqu'une quantité est retournée.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
                cboMotifRetour.requestFocus();
                return;
            }
        }

        // Résoudre tarif
        TarifService.TarifResolu tr;
        try {
            tr = tarifService.resoudre(pr.getId(), cl, qSort, fiche.getDateFiche());
        } catch (Exception ex) {
            tr = new TarifService.TarifResolu(BigDecimal.ZERO, "Standard", BigDecimal.ZERO, BigDecimal.ZERO, null);
        }

        LigneSortie l = new LigneSortie();
        l.setFicheId(fiche.getId());
        l.setClient(cl);
        l.setProduit(pr);
        l.setQuantiteSortie(qSort);
        l.setQuantiteRetournee(qRet);
        l.setTarifApplicable(tr.prix());
        l.setTypeTarif(tr.typeTarif());

        try {
            l.setRemisePct(new BigDecimal(txtRemisePct.getText().replace(",", ".")));
        } catch (Exception e) {
            l.setRemisePct(BigDecimal.ZERO);
        }
        if (qRet > 0) l.setMotifRetour((String) cboMotifRetour.getSelectedItem());

        ficheDAO.saveLigne(l);
        fiche.getLignes().add(l);
        appendLigneDansTable(l);
        recalculerTotaux();

        // Réinitialiser les champs
        spnSortie.setValue(0); spnRetour.setValue(0);
        cboMotifRetour.setSelectedIndex(0);
        txtRemisePct.setText("0");
    }

    private void appendLigneDansTable(LigneSortie l) {
        String statut = l.getClient() != null
            ? l.getClient().getStatut() != null ? l.getClient().getStatut().name() : "Actif"
            : "—";
        lignesModel.addRow(new Object[]{
            l.getClient()  != null ? l.getClient().getNom()   : "—",
            statut,
            l.getProduit() != null ? l.getProduit().getLibelle() : "—",
            l.getQuantiteSortie(),
            l.getQuantiteRetournee(),
            l.getQuantiteNette(),
            FormatUtil.montant(l.getTarifApplicable()),
            l.getTypeTarif() != null ? l.getTypeTarif() : "—",
            FormatUtil.montant(l.getRemisePct()),
            FormatUtil.montant(l.getMontantHt()),
            l.getMotifRetour() != null ? l.getMotifRetour() : "—"
        });
    }

    private void proposerSuppression() {
        int row = lignesTable.getSelectedRow();
        if (row < 0 || row >= fiche.getLignes().size()) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Supprimer cette ligne de sortie ?", "Suppression", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        LigneSortie l = fiche.getLignes().get(row);
        if (l.getId() != null) ficheDAO.deleteLigne(l.getId());
        fiche.getLignes().remove(row);
        lignesModel.removeRow(row);
        recalculerTotaux();
    }

    private void recalculerTotaux() {
        fiche.recalculerTotaux();
        ficheDAO.updateTotaux(fiche.getId(), fiche.getTotalSorties(), fiche.getTotalRetours(), fiche.getTotalNet());
        long nbCl = fiche.getLignes().stream()
            .map(l -> l.getClient() != null ? l.getClient().getId() : "").distinct().count();
        lblNbClients.setText("<html><small>Nb clients :</small><br><b>" + nbCl + "</b></html>");
        lblTotalSorties.setText("<html><small>Total sorties :</small><br><b>"
            + FormatUtil.montant(fiche.getTotalSorties()) + " FCFA</b></html>");
        lblTotalRetours.setText("<html><small>Total retours :</small><br><b>"
            + FormatUtil.montant(fiche.getTotalRetours()) + " FCFA</b></html>");
        lblTotalNet.setText("<html><small>Total net :</small><br><b>"
            + FormatUtil.montant(fiche.getTotalNet()) + " FCFA</b></html>");
    }

    private void enregistrerBrouillon() {
        ficheDAO.updateStatut(fiche.getId(), FicheJournaliere.Statut.Brouillon);
        fiche.setStatut(FicheJournaliere.Statut.Brouillon);
        lblStatut.setText("État : Brouillon");
        JOptionPane.showMessageDialog(this, "Fiche enregistrée en brouillon.", "Brouillon", JOptionPane.INFORMATION_MESSAGE);
    }

    private void finaliser() {
        if (fiche.getLignes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucune ligne à finaliser.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ficheDAO.updateStatut(fiche.getId(), FicheJournaliere.Statut.Complétée);
        fiche.setStatut(FicheJournaliere.Statut.Complétée);
        lblStatut.setText("État : Complétée");
        JOptionPane.showMessageDialog(this,
            "Fiche finalisée.\nVous pouvez maintenant générer les factures depuis la liste.",
            "Finalisé", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private void ouvrirDeblocage() {
        Client cl = (Client) cboClient.getSelectedItem();
        if (cl == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez d'abord un client.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DeblocageDialog dlg = new DeblocageDialog((Frame) getOwner(), cl);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            // Recharger le client mis à jour
            clientDAO.findById(cl.getId()).ifPresent(updated -> {
                for (int i = 0; i < cboClient.getItemCount(); i++) {
                    if (cboClient.getItemAt(i).getId().equals(updated.getId())) {
                        cboClient.removeItemAt(i);
                        cboClient.insertItemAt(updated, i);
                        cboClient.setSelectedIndex(i);
                        break;
                    }
                }
            });
            verifierBlocageClient();
        }
    }
}
