package com.boulangerie.ui.dialogs;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProduitDialog extends JDialog {
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final AuditDAO   auditDAO   = new AuditDAO();
    private final SessionService session = SessionService.getInstance();
    private boolean saved = false;

    private final Produit produit;
    private JTextField txtCode, txtLibelle, txtUnite, txtSeuil;
    private JTextArea  txtDesc;
    private JComboBox<Famille> cboFamille;
    private JComboBox<String>  cboStatut;
    private DefaultTableModel tarifsModel;

    public ProduitDialog(Frame parent, Produit produit) {
        super(parent, produit == null ? "Nouveau Produit" : "Fiche Produit — " + produit.getCode(), true);
        this.produit = produit != null ? produit : new Produit();
        setSize(680, 580);
        setLocationRelativeTo(parent);
        buildUI();
        if (produit != null) remplirFormulaire();
    }

    private void buildUI() {
        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        getContentPane().setBackground(UIConstants.GRIS_FOND);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(320);
        split.setOpaque(false);

        // ── Gauche : infos produit ─────────────────────────────────
        JPanel left = new JPanel(new GridBagLayout());
        left.setBackground(Color.WHITE);
        left.setBorder(BorderFactory.createTitledBorder("Informations produit"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        txtCode    = addField(left, gc, "Code produit *", 0);
        txtLibelle = addField(left, gc, "Libellé *", 1);

        gc.gridx = 0; gc.gridy = 2; left.add(new JLabel("Famille"), gc);
        gc.gridx = 1; gc.gridy = 2; gc.gridwidth = 2;
        List<Famille> fams = produitDAO.findAllFamilles();
        cboFamille = new JComboBox<>();
        cboFamille.addItem(new Famille("","—")); fams.forEach(cboFamille::addItem);
        left.add(cboFamille, gc); gc.gridwidth = 1;

        txtUnite = addField(left, gc, "Unité *", 3);
        txtUnite.setText("Pièce");

        gc.gridx = 0; gc.gridy = 4; left.add(new JLabel("Statut"), gc);
        gc.gridx = 1; gc.gridy = 4; gc.gridwidth = 2;
        cboStatut = new JComboBox<>(new String[]{"Actif", "Inactif"});
        left.add(cboStatut, gc); gc.gridwidth = 1;

        txtSeuil = addField(left, gc, "Seuil alerte", 5);
        txtSeuil.setText("0");

        gc.gridx = 0; gc.gridy = 6; left.add(new JLabel("Description"), gc);
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 3; gc.gridheight = 2;
        txtDesc = new JTextArea(3, 20);
        txtDesc.setLineWrap(true); txtDesc.setWrapStyleWord(true);
        left.add(new JScrollPane(txtDesc), gc);

        split.setLeftComponent(left);

        // ── Droite : tarifs ────────────────────────────────────────
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setBackground(Color.WHITE);
        right.setBorder(BorderFactory.createTitledBorder("Tarifs"));

        String[] tarifsCol = {"Type de tarif", "Prix (FCFA)", "Date début", "Date fin", "Statut"};
        tarifsModel = new DefaultTableModel(tarifsCol, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tarifsTable = new JTable(tarifsModel);
        tarifsTable.setFont(UIConstants.FONT_NORMAL);
        tarifsTable.setRowHeight(28);
        right.add(new JScrollPane(tarifsTable), BorderLayout.CENTER);

        JPanel tarifsBtn = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tarifsBtn.setOpaque(false);
        RoundedButton btnAddTarif = new RoundedButton("+ Ajouter un tarif", RoundedButton.Style.OUTLINE);
        btnAddTarif.addActionListener(e -> ajouterTarif());
        tarifsBtn.add(btnAddTarif);
        right.add(tarifsBtn, BorderLayout.SOUTH);

        split.setRightComponent(right);

        // ── Boutons action ────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        footer.setOpaque(false);
        RoundedButton btnSave   = new RoundedButton("Enregistrer", RoundedButton.Style.PRIMARY);
        RoundedButton btnCancel = new RoundedButton("Annuler",     RoundedButton.Style.SECONDARY);
        RoundedButton btnDup    = new RoundedButton("Dupliquer",   RoundedButton.Style.OUTLINE);
        btnSave.addActionListener(e -> sauvegarder());
        btnCancel.addActionListener(e -> dispose());
        btnDup.addActionListener(e -> dupliquer());
        footer.add(btnSave); footer.add(btnCancel); footer.add(btnDup);

        add(split, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void remplirFormulaire() {
        txtCode.setText(produit.getCode());
        txtLibelle.setText(produit.getLibelle());
        txtUnite.setText(produit.getUnite());
        txtSeuil.setText(String.valueOf(produit.getSeuilAlerte()));
        txtDesc.setText(produit.getDescription());
        if (produit.getStatut() != null) cboStatut.setSelectedItem(produit.getStatut().name());
        if (produit.getFamille() != null) {
            for (int i = 0; i < cboFamille.getItemCount(); i++) {
                if (cboFamille.getItemAt(i).getId().equals(produit.getFamille().getId())) {
                    cboFamille.setSelectedIndex(i); break;
                }
            }
        }
        // Charger tarifs
        if (produit.getId() != null) {
            List<Tarif> tarifs = produitDAO.findTarifs(produit.getId());
            tarifsModel.setRowCount(0);
            for (Tarif t : tarifs) {
                tarifsModel.addRow(new Object[]{
                    t.getTypeTarif().name(),
                    FormatUtil.montant(t.getMontant()),
                    FormatUtil.date(t.getDateDebut()),
                    FormatUtil.date(t.getDateFin()),
                    t.getStatut().name()
                });
            }
        }
    }

    private void sauvegarder() {
        if (txtCode.getText().isBlank() || txtLibelle.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Code et Libellé sont obligatoires.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            produit.setCode(txtCode.getText().trim());
            produit.setLibelle(txtLibelle.getText().trim());
            produit.setUnite(txtUnite.getText().trim());
            produit.setSeuilAlerte(Integer.parseInt(txtSeuil.getText().trim()));
            produit.setDescription(txtDesc.getText().trim());
            produit.setStatut(Produit.Statut.valueOf((String) cboStatut.getSelectedItem()));
            Famille f = (Famille) cboFamille.getSelectedItem();
            if (f != null && !f.getId().isEmpty()) produit.setFamille(f);

            if (produit.getId() == null || produit.getId().isBlank()) {
                String id = produitDAO.save(produit);
                produit.setId(id);
                auditDAO.log(new JournalAudit("Produit", id, JournalAudit.CREATE,
                    session.getUserId(), session.getLogin(), "Nouveau produit: " + produit.getCode()));
            } else {
                produitDAO.update(produit);
                auditDAO.log(new JournalAudit("Produit", produit.getId(), JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(), "Modification: " + produit.getCode()));
            }
            saved = true;
            dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Le seuil d'alerte doit être un entier.", "Erreur", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ajouterTarif() {
        if (produit.getId() == null) {
            JOptionPane.showMessageDialog(this, "Enregistrez d'abord le produit.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Dialog simplifié d'ajout de tarif
        JDialog d = new JDialog(this, "Ajouter un tarif", true);
        d.setSize(350, 250);
        d.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridLayout(5, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JComboBox<String> cboType = new JComboBox<>(new String[]{"Standard","Externe","Interne","Carrefour","Specifique"});
        JTextField txtPrix  = new JTextField("0.00");
        JTextField txtDebut = new JTextField(FormatUtil.date(LocalDate.now()));
        JTextField txtFin   = new JTextField("");
        p.add(new JLabel("Type :")); p.add(cboType);
        p.add(new JLabel("Prix FCFA :")); p.add(txtPrix);
        p.add(new JLabel("Début (dd/MM/yyyy) :")); p.add(txtDebut);
        p.add(new JLabel("Fin (dd/MM/yyyy) :")); p.add(txtFin);
        JButton btnOk = new JButton("Ajouter");
        btnOk.addActionListener(ev -> {
            try {
                Tarif t = new Tarif();
                t.setProduitId(produit.getId());
                t.setTypeTarif(Tarif.TypeTarif.valueOf((String) cboType.getSelectedItem()));
                t.setMontant(new BigDecimal(txtPrix.getText().replace(",",".")));
                t.setDateDebut(FormatUtil.parseDate(txtDebut.getText()));
                String finStr = txtFin.getText().trim();
                if (!finStr.isEmpty()) t.setDateFin(FormatUtil.parseDate(finStr));
                t.setStatut(Tarif.Statut.Actif);
                produitDAO.saveTarif(t);
                tarifsModel.addRow(new Object[]{
                    t.getTypeTarif().name(),
                    FormatUtil.montant(t.getMontant()),
                    FormatUtil.date(t.getDateDebut()),
                    FormatUtil.date(t.getDateFin()),
                    "Actif"
                });
                d.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Erreur: " + ex.getMessage());
            }
        });
        p.add(btnOk);
        d.add(p);
        d.setVisible(true);
    }

    private void dupliquer() {
        Produit copie = new Produit();
        copie.setCode(txtCode.getText() + "_COPIE");
        copie.setLibelle(txtLibelle.getText() + " (copie)");
        copie.setUnite(txtUnite.getText());
        ProduitDialog dlg = new ProduitDialog((Frame) getOwner(), copie);
        dlg.setVisible(true);
        if (dlg.isSaved()) { saved = true; dispose(); }
    }

    private JTextField addField(JPanel p, GridBagConstraints gc, String label, int row) {
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.gridy = row; gc.gridwidth = 2;
        JTextField tf = new JTextField(16);
        p.add(tf, gc);
        gc.gridwidth = 1;
        return tf;
    }

    public boolean isSaved() { return saved; }
}
