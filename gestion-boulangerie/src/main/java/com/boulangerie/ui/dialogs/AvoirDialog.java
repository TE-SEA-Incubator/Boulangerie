package com.boulangerie.ui.dialogs;

import com.boulangerie.model.Avoir;
import com.boulangerie.model.Facture;
import com.boulangerie.service.FacturationService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Dialogue de création d'un avoir sur une facture verrouillée.
 * CDC §5.3 + §12 : Toute correction d'une facture verrouillée passe
 * par un avoir visible dans le journal d'audit.
 */
public class AvoirDialog extends JDialog {

    private final Facture            facture;
    private final FacturationService factService = new FacturationService();
    private boolean saved = false;
    private Avoir   avoir;

    private JTextField txtMontant;
    private JTextArea  txtMotif;
    private JLabel     lblFactureInfo, lblSoldeRestant;

    public AvoirDialog(Frame parent, Facture facture) {
        super(parent, "Créer un avoir — " + facture.getNumero(), true);
        this.facture = facture;
        setSize(500, 400);
        setLocationRelativeTo(parent);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(UIConstants.GRIS_FOND);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(UIConstants.GRIS_FOND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // ── Info facture ──────────────────────────────────────────
        JPanel infoPanel = new JPanel(new GridLayout(2, 2, 8, 4));
        infoPanel.setBackground(UIConstants.BLEU_CLAIR);
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.BLEU_PRIMAIRE, 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        addInfoRow(infoPanel, "Facture :", facture.getNumero());
        addInfoRow(infoPanel, "Client :",
            facture.getClient() != null ? facture.getClient().getNom() : "Anonyme");
        addInfoRow(infoPanel, "Montant TTC :",
            FormatUtil.montant(facture.getMontantTtc()) + " FCFA");
        addInfoRow(infoPanel, "Statut :", facture.getStatut().name());

        // ── Formulaire ────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        // Montant avoir
        gc.gridx = 0; gc.gridy = 0;
        form.add(new JLabel("Montant de l'avoir (FCFA) *"), gc);
        gc.gridx = 1; gc.weightx = 1;
        txtMontant = new JTextField("0.00");
        form.add(txtMontant, gc);
        txtMontant.addActionListener(e -> majSoldeRestant());
        txtMontant.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { majSoldeRestant(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { majSoldeRestant(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { majSoldeRestant(); }
        });

        // Solde restant
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        form.add(new JLabel("Montant restant après avoir :"), gc);
        gc.gridx = 1; gc.weightx = 1;
        lblSoldeRestant = new JLabel(FormatUtil.montant(facture.getMontantTtc()) + " FCFA");
        lblSoldeRestant.setFont(UIConstants.FONT_BOLD);
        form.add(lblSoldeRestant, gc);

        // Motif
        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0;
        form.add(new JLabel("Motif de l'avoir *"), gc);
        gc.gridx = 1; gc.gridy = 2; gc.weightx = 1; gc.gridheight = 3;
        txtMotif = new JTextArea(4, 25);
        txtMotif.setLineWrap(true); txtMotif.setWrapStyleWord(true);
        txtMotif.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        form.add(new JScrollPane(txtMotif), gc);
        gc.gridheight = 1;

        // Avertissement verrouillage
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2;
        JLabel lblWarn = new JLabel("🔒 La facture reste verrouillée. L'avoir sera visible dans le journal d'audit.");
        lblWarn.setFont(UIConstants.FONT_PETIT);
        lblWarn.setForeground(UIConstants.GRIS_TEXTE);
        form.add(lblWarn, gc);

        // ── Boutons ───────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        RoundedButton btnCreer  = new RoundedButton("✓ Créer l'avoir", RoundedButton.Style.SUCCESS);
        RoundedButton btnAnnul  = new RoundedButton("Annuler",          RoundedButton.Style.SECONDARY);
        btnCreer.addActionListener(e  -> creerAvoir());
        btnAnnul.addActionListener(e  -> dispose());
        footer.add(btnAnnul); footer.add(btnCreer);

        root.add(infoPanel, BorderLayout.NORTH);
        root.add(form,       BorderLayout.CENTER);
        root.add(footer,     BorderLayout.SOUTH);
        add(root);
    }

    private void majSoldeRestant() {
        try {
            BigDecimal montant   = new BigDecimal(txtMontant.getText().trim().replace(",", "."));
            BigDecimal restant   = facture.getMontantTtc().subtract(montant);
            lblSoldeRestant.setText(FormatUtil.montant(restant) + " FCFA");
            lblSoldeRestant.setForeground(
                restant.compareTo(BigDecimal.ZERO) < 0 ? UIConstants.ROUGE_DANGER : UIConstants.VERT_SUCCES);
        } catch (Exception ignore) {
            lblSoldeRestant.setText("—");
        }
    }

    private void creerAvoir() {
        String montantStr = txtMontant.getText().trim().replace(",", ".");
        String motif      = txtMotif.getText().trim();

        if (montantStr.isBlank() || motif.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Le montant et le motif sont obligatoires.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal montant;
        try {
            montant = new BigDecimal(montantStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Montant invalide.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this,
                "Le montant doit être supérieur à zéro.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (montant.compareTo(facture.getMontantTtc()) > 0) {
            JOptionPane.showMessageDialog(this,
                "Le montant de l'avoir ne peut dépasser le montant de la facture.",
                "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            avoir = factService.creerAvoir(facture, montant, motif);
            saved = true;
            JOptionPane.showMessageDialog(this,
                "Avoir " + avoir.getNumero() + " créé avec succès.\n"
                + "Montant : " + FormatUtil.montant(montant) + " FCFA",
                "Avoir créé", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Accès refusé", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addInfoRow(JPanel panel, String label, String value) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_PETIT);
        lbl.setForeground(UIConstants.GRIS_TEXTE);
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BOLD);
        panel.add(lbl);
        panel.add(val);
    }

    public boolean isSaved() { return saved; }
    public Avoir getAvoir()  { return avoir; }
}
