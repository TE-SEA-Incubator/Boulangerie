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
    private JTextField txtCode, txtLibelle, txtPrix, txtUnite, txtSeuil;
    private JTextArea  txtDesc;
    private JComboBox<Famille> cboFamille;
    private JComboBox<String>  cboStatut;

    public ProduitDialog(Frame parent, Produit produit) {
        super(parent, produit == null ? "Nouveau Produit" : "Fiche Produit — " + produit.getCode(), true);
        this.produit = produit != null ? produit : new Produit();
        setSize(480, 520);
        setLocationRelativeTo(parent);
        buildUI();
        if (produit != null) remplirFormulaire();
    }

    private void buildUI() {
        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        getContentPane().setBackground(UIConstants.GRIS_FOND);

        // ── Formulaire infos produit ─────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 224, 230)),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        txtCode = addField(form, gc, "Code produit (auto)", 0);
        txtCode.setEditable(false);
        txtCode.setBackground(new Color(245, 246, 248));

        txtLibelle = addField(form, gc, "Libellé *", 1);

        gc.gridx = 0; gc.gridy = 2; form.add(new JLabel("Famille *"), gc);
        gc.gridx = 1; gc.gridy = 2; gc.gridwidth = 1;
        List<Famille> fams = produitDAO.findAllFamilles();
        cboFamille = new JComboBox<>();
        fams.forEach(cboFamille::addItem);
        
        JButton btnAddFam = new JButton("+");
        btnAddFam.setToolTipText("Ajouter une nouvelle famille");
        btnAddFam.addActionListener(e -> {
            String nom = JOptionPane.showInputDialog(this, "Nom de la nouvelle famille :", "Nouvelle Famille", JOptionPane.QUESTION_MESSAGE);
            if (nom != null && !nom.trim().isEmpty()) {
                try {
                    Famille nf = produitDAO.saveFamille(nom.trim());
                    cboFamille.addItem(nf);
                    cboFamille.setSelectedItem(nf);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JPanel famPanel = new JPanel(new BorderLayout(4, 0));
        famPanel.setOpaque(false);
        famPanel.add(cboFamille, BorderLayout.CENTER);
        famPanel.add(btnAddFam, BorderLayout.EAST);
        form.add(famPanel, gc);

        txtPrix = addField(form, gc, "Prix unitaire (FCFA) *", 3);
        txtPrix.setText("0");

        txtUnite = addField(form, gc, "Unité *", 4);
        txtUnite.setText("Pièce");

        gc.gridx = 0; gc.gridy = 5; form.add(new JLabel("Statut"), gc);
        gc.gridx = 1; gc.gridy = 5;
        cboStatut = new JComboBox<>(new String[]{"Actif", "Inactif"});
        form.add(cboStatut, gc);

        txtSeuil = addField(form, gc, "Seuil alerte", 6);
        txtSeuil.setText("0");

        gc.gridx = 0; gc.gridy = 7; form.add(new JLabel("Description"), gc);
        gc.gridx = 1; gc.gridy = 7; gc.gridheight = 2;
        txtDesc = new JTextArea(3, 20);
        txtDesc.setLineWrap(true); txtDesc.setWrapStyleWord(true);
        form.add(new JScrollPane(txtDesc), gc);
        gc.gridheight = 1;

        if (produit.getId() == null) {
            String famId = cboFamille.getSelectedItem() != null ? ((Famille) cboFamille.getSelectedItem()).getId() : null;
            txtCode.setText(produitDAO.genererCode(famId));
        }

        cboFamille.addActionListener(e -> {
            if (produit.getId() == null && cboFamille.getSelectedItem() != null) {
                txtCode.setText(produitDAO.genererCode(((Famille) cboFamille.getSelectedItem()).getId()));
            }
        });

        // ── Boutons action ────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        RoundedButton btnSave   = new RoundedButton("Enregistrer", RoundedButton.Style.PRIMARY);
        RoundedButton btnCancel = new RoundedButton("Annuler",     RoundedButton.Style.SECONDARY);
        btnSave.addActionListener(e -> sauvegarder());
        btnCancel.addActionListener(e -> dispose());
        footer.add(btnCancel); footer.add(btnSave);

        add(form, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void remplirFormulaire() {
        txtCode.setText(produit.getCode());
        txtLibelle.setText(produit.getLibelle());
        if (produit.getPrixUnitaire() != null) txtPrix.setText(produit.getPrixUnitaire().toPlainString());
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
    }

    private void sauvegarder() {
        if (txtLibelle.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Le libellé est obligatoire.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            produit.setCode(txtCode.getText().trim());
            produit.setLibelle(txtLibelle.getText().trim());
            produit.setUnite(txtUnite.getText().trim());
            BigDecimal px = new BigDecimal(txtPrix.getText().trim().replace(',', '.'));
            if (px.signum() < 0) throw new NumberFormatException();
            produit.setPrixUnitaire(px);
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
            JOptionPane.showMessageDialog(this, "Vérifiez les valeurs numériques (prix ou seuil).", "Erreur", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
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
