package com.boulangerie.ui.dialogs;

import com.boulangerie.model.AutorisationDeblocage;
import com.boulangerie.model.Client;
import com.boulangerie.service.DeblocageService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Dialogue de déblocage exceptionnel — réservé au Manager / Admin.
 * Reproduit le workflow CDC §5.7 et le diagramme de séquence correspondant.
 *
 * Workflow :
 *   Blocage → Justification → Autorisation Manager
 *   (motif + engagement + montant + durée)
 *   → Sortie exceptionnelle autorisée → Dette conservée → Nouveau suivi
 */
public class DeblocageDialog extends JDialog {

    private final Client           client;
    private final DeblocageService deblocageService = new DeblocageService();
    private boolean confirmed = false;
    private AutorisationDeblocage autorisation;

    private JTextArea  txtMotif;
    private JTextArea  txtEngagement;
    private JTextField txtMontant;
    private JTextField txtDuree;

    public DeblocageDialog(Frame parent, Client client) {
        super(parent, "Déblocage exceptionnel — " + client.getNom(), true);
        this.client = client;
        setSize(580, 520);
        setLocationRelativeTo(parent);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(UIConstants.GRIS_FOND);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(UIConstants.GRIS_FOND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // ── En-tête alerte ────────────────────────────────────────
        JPanel alertPanel = new JPanel(new BorderLayout(8, 4));
        alertPanel.setBackground(UIConstants.ORANGE_CLAIR);
        alertPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.ORANGE_ALERTE, 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel lblAlerte = new JLabel("⚠  Client bloqué — Déblocage exceptionnel requis");
        lblAlerte.setFont(UIConstants.FONT_BOLD);
        lblAlerte.setForeground(new Color(0x8A5A00));

        JPanel clientInfo = new JPanel(new GridLayout(1, 4, 16, 0));
        clientInfo.setOpaque(false);
        addInfoItem(clientInfo, "Client", client.getNom());
        addInfoItem(clientInfo, "Code", client.getCode());
        addInfoItem(clientInfo, "Solde actuel", FormatUtil.montant(client.getSoldeActuel()) + " FCFA");
        addInfoItem(clientInfo, "Plafond crédit", FormatUtil.montant(client.getPlafondCredit()) + " FCFA");

        alertPanel.add(lblAlerte,  BorderLayout.NORTH);
        alertPanel.add(clientInfo, BorderLayout.CENTER);

        // ── Formulaire autorisation ───────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        // Motif (obligatoire)
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        form.add(labelRequired("Motif du déblocage *"), gc);
        gc.gridx = 1; gc.gridy = 0; gc.weightx = 1; gc.gridwidth = 2;
        txtMotif = new JTextArea(3, 30);
        txtMotif.setLineWrap(true); txtMotif.setWrapStyleWord(true);
        txtMotif.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        form.add(new JScrollPane(txtMotif), gc);
        gc.gridwidth = 1;

        // Engagement client
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        form.add(new JLabel("Engagement du client"), gc);
        gc.gridx = 1; gc.gridy = 1; gc.weightx = 1; gc.gridwidth = 2;
        txtEngagement = new JTextArea(3, 30);
        txtEngagement.setLineWrap(true); txtEngagement.setWrapStyleWord(true);
        txtEngagement.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        form.add(new JScrollPane(txtEngagement), gc);
        gc.gridwidth = 1;

        // Montant autorisé
        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0;
        form.add(new JLabel("Montant autorisé (FCFA)"), gc);
        gc.gridx = 1; gc.gridy = 2; gc.weightx = 0.5;
        txtMontant = new JTextField("0");
        form.add(txtMontant, gc);

        // Durée de validité
        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0;
        form.add(new JLabel("Validité jusqu'au (dd/MM/yyyy)"), gc);
        gc.gridx = 1; gc.gridy = 3;
        txtDuree = new JTextField(FormatUtil.date(java.time.LocalDate.now().plusDays(7)));
        form.add(txtDuree, gc);

        // Note : dette conservée
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 3;
        JLabel lblNote = new JLabel("ℹ La dette du client est conservée. Cette autorisation permet une sortie exceptionnelle uniquement.");
        lblNote.setFont(UIConstants.FONT_PETIT);
        lblNote.setForeground(UIConstants.GRIS_TEXTE);
        form.add(lblNote, gc);
        gc.gridwidth = 1;

        // ── Boutons ───────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        RoundedButton btnOk     = new RoundedButton("✓ Autoriser le déblocage", RoundedButton.Style.SUCCESS);
        RoundedButton btnCancel = new RoundedButton("Annuler",                   RoundedButton.Style.SECONDARY);
        btnOk.addActionListener(e -> valider());
        btnCancel.addActionListener(e -> dispose());
        footer.add(btnCancel); footer.add(btnOk);

        root.add(alertPanel, BorderLayout.NORTH);
        root.add(form,       BorderLayout.CENTER);
        root.add(footer,     BorderLayout.SOUTH);
        add(root);
    }

    private void valider() {
        if (txtMotif.getText().trim().isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Le motif du déblocage est obligatoire.", "Validation", JOptionPane.WARNING_MESSAGE);
            txtMotif.requestFocus();
            return;
        }

        try {
            BigDecimal montant = new BigDecimal(txtMontant.getText().trim().replace(",", "."));
            java.time.LocalDate duree = FormatUtil.parseDate(txtDuree.getText().trim());

            int confirm = JOptionPane.showConfirmDialog(this,
                "<html>Confirmer le déblocage exceptionnel de <b>" + client.getNom() + "</b> ?<br>"
                + "La dette est conservée. Cette action sera journalisée.</html>",
                "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            autorisation = deblocageService.debloquerExceptionnel(
                client.getId(),
                txtMotif.getText().trim(),
                txtEngagement.getText().trim(),
                montant,
                duree
            );
            confirmed = true;
            JOptionPane.showMessageDialog(this,
                "Déblocage autorisé avec succès.\nSortie exceptionnelle permise jusqu'au "
                + (duree != null ? FormatUtil.date(duree) : "date non définie") + ".",
                "Déblocage autorisé", JOptionPane.INFORMATION_MESSAGE);
            dispose();

        } catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Accès refusé", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addInfoItem(JPanel panel, String label, String value) {
        JPanel item = new JPanel(new BorderLayout(0, 2));
        item.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(UIConstants.FONT_PETIT);
        lbl.setForeground(UIConstants.GRIS_TEXTE);
        JLabel val = new JLabel(value);
        val.setFont(UIConstants.FONT_BOLD);
        item.add(lbl, BorderLayout.NORTH);
        item.add(val, BorderLayout.CENTER);
        panel.add(item);
    }

    private JLabel labelRequired(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConstants.FONT_BOLD);
        return l;
    }

    public boolean isConfirmed() { return confirmed; }
    public AutorisationDeblocage getAutorisation() { return autorisation; }
}
