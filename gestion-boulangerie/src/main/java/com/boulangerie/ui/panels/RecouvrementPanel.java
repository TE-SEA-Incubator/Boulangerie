package com.boulangerie.ui.panels;

import com.boulangerie.dao.VersementDAO;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Module Rapprochement & Clôture de caisse.
 * Reproduit l'écran n°12 de la Planche 2.
 */
public class RecouvrementPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame mainFrame;
    private final VersementDAO versementDAO = new VersementDAO();
    private final CaisseService caisseService = new CaisseService();
    private final SessionService session = SessionService.getInstance();

    private JLabel lblAttendu, lblRemis, lblEnregistre;
    private JLabel lblEcartVal, lblMotifEcart;
    private JLabel lblObjectif, lblRealise, lblTaux;
    private JLabel lblSoldeCloture;
    private JComboBox<String> cboMotif;
    private RoundedButton btnValider;

    public RecouvrementPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JLabel lbl = new JLabel("Rapprochement & Clôture de caisse");
        lbl.setFont(UIConstants.FONT_TITRE);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        // ── 3 blocs montants ──────────────────────────────────────
        JPanel montantsPanel = new JPanel(new GridLayout(1, 3, 16, 0));
        montantsPanel.setOpaque(false);
        montantsPanel.add(buildMontantCard("Attendu (TTC)", "—", UIConstants.GRIS_TEXTE, true));
        montantsPanel.add(buildMontantCard("Remis (TTC)", "—", UIConstants.GRIS_TEXTE, false));
        montantsPanel.add(buildMontantCard("Enregistré (TTC)", "—", UIConstants.VERT_SUCCES, false));

        // ── Bloc écart ────────────────────────────────────────────
        JPanel ecartPanel = new JPanel(new BorderLayout(8, 4));
        ecartPanel.setBackground(UIConstants.ROUGE_CLAIR);
        ecartPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.ROUGE_DANGER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        lblEcartVal = new JLabel("Écart constaté : —");
        lblEcartVal.setFont(UIConstants.FONT_BOLD);
        lblEcartVal.setForeground(UIConstants.ROUGE_DANGER);

        JLabel lblMotifLabel = new JLabel("Motif de l'écart (obligatoire) :");
        lblMotifLabel.setFont(UIConstants.FONT_NORMAL);
        cboMotif = new JComboBox<>(new String[]{
            "—", "Chèques en attente d'encaissement", "Erreur de comptage",
            "Dépôt partiel", "Autre"
        });

        JPanel chkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chkPanel.setOpaque(false);
        JCheckBox chkVerif = new JCheckBox("Chaque versement dispose d'un reçu associé");
        chkVerif.setOpaque(false); chkVerif.setFont(UIConstants.FONT_NORMAL);
        chkPanel.add(chkVerif);

        ecartPanel.add(lblEcartVal, BorderLayout.NORTH);
        JPanel motifRow = new JPanel(new BorderLayout(8,0));
        motifRow.setOpaque(false);
        motifRow.add(lblMotifLabel, BorderLayout.WEST);
        motifRow.add(cboMotif, BorderLayout.CENTER);
        ecartPanel.add(motifRow, BorderLayout.CENTER);
        ecartPanel.add(chkPanel, BorderLayout.SOUTH);

        // ── Résultat recouvrement ─────────────────────────────────
        JPanel recouvrPanel = new JPanel(new GridLayout(2, 3, 16, 8));
        recouvrPanel.setBackground(Color.WHITE);
        recouvrPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Résultat du recouvrement"),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        recouvrPanel.add(new JLabel("Objectif (TTC)")); recouvrPanel.add(new JLabel("Réalisé (TTC)")); recouvrPanel.add(new JLabel("Taux de recouvrement"));
        lblObjectif = new JLabel("—"); lblObjectif.setFont(UIConstants.FONT_BOLD);
        lblRealise  = new JLabel("—"); lblRealise.setFont(UIConstants.FONT_BOLD);
        lblTaux     = new JLabel("—"); lblTaux.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTaux.setForeground(UIConstants.VERT_SUCCES);
        recouvrPanel.add(lblObjectif); recouvrPanel.add(lblRealise); recouvrPanel.add(lblTaux);

        // ── Solde de clôture ──────────────────────────────────────
        JPanel soldePanel = new JPanel(new BorderLayout(8, 0));
        soldePanel.setBackground(UIConstants.BLEU_CLAIR);
        soldePanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel lblSoldeLabel = new JLabel("Solde de clôture J = Solde d'ouverture J+1");
        lblSoldeLabel.setFont(UIConstants.FONT_NORMAL);
        lblSoldeCloture = new JLabel("—");
        lblSoldeCloture.setFont(UIConstants.FONT_BOLD);
        lblSoldeCloture.setForeground(UIConstants.ROUGE_DANGER);
        soldePanel.add(lblSoldeLabel, BorderLayout.WEST);
        soldePanel.add(lblSoldeCloture, BorderLayout.EAST);

        // ── Bouton valider ────────────────────────────────────────
        btnValider = new RoundedButton("🔒  Valider et clôturer", RoundedButton.Style.PRIMARY);
        btnValider.addActionListener(e -> cloturerJour());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        btnPanel.add(btnValider);

        // ── Assemblage ────────────────────────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        gc.insets = new Insets(0, 0, 12, 0);
        gc.gridy = 0; center.add(montantsPanel, gc);
        gc.gridy = 1; center.add(ecartPanel,    gc);
        gc.gridy = 2; center.add(recouvrPanel,  gc);
        gc.gridy = 3; center.add(soldePanel,    gc);
        gc.gridy = 4; center.add(btnPanel,      gc);

        add(lbl,    BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JPanel buildMontantCard(String titre, String valeur, Color color, boolean showLabel) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel lblT = new JLabel(titre); lblT.setFont(UIConstants.FONT_BOLD);
        JLabel lblV = new JLabel(valeur); lblV.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblV.setForeground(color);
        p.add(lblT, BorderLayout.NORTH);
        p.add(lblV, BorderLayout.CENTER);
        // stocker les labels pour refresh
        if (titre.contains("Attendu"))     lblAttendu    = lblV;
        else if (titre.contains("Remis"))  lblRemis      = lblV;
        else if (titre.contains("nregistré")) lblEnregistre = lblV;
        return p;
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        SwingWorker<BigDecimal[], Void> w = new SwingWorker<>() {
            @Override protected BigDecimal[] doInBackground() {
                return new BigDecimal[]{
                    versementDAO.getMontantAttenduJour(today),
                    versementDAO.getMontantRemisJour(today),
                    versementDAO.getMontantEnregistreJour(today)
                };
            }
            @Override protected void done() {
                try {
                    BigDecimal[] d = get();
                    if (lblAttendu    != null) lblAttendu.setText(FormatUtil.montant(d[0]));
                    if (lblRemis      != null) lblRemis.setText(FormatUtil.montant(d[1]));
                    if (lblEnregistre != null) lblEnregistre.setText(FormatUtil.montant(d[2]));
                    BigDecimal ecart = d[1].subtract(d[2]);
                    if (lblEcartVal != null) lblEcartVal.setText("Écart constaté : " + FormatUtil.montant(ecart));
                    if (lblObjectif != null) lblObjectif.setText(FormatUtil.montant(d[0]));
                    if (lblRealise  != null) lblRealise.setText(FormatUtil.montant(d[2]));
                    if (lblTaux != null && d[0].compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal taux = d[2].divide(d[0], 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                        lblTaux.setText(taux + " %");
                        lblTaux.setForeground(taux.compareTo(BigDecimal.valueOf(80)) >= 0 ? UIConstants.VERT_SUCCES : UIConstants.ROUGE_DANGER);
                    }
                    if (lblSoldeCloture != null) lblSoldeCloture.setText(FormatUtil.montant(ecart));
                } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void cloturerJour() {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            JOptionPane.showMessageDialog(this, "Accès refusé.", "Permission", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String motif = (String) cboMotif.getSelectedItem();
        int confirm = JOptionPane.showConfirmDialog(this,
            "Confirmer la clôture de caisse pour le " + FormatUtil.date(LocalDate.now()) + " ?",
            "Confirmation clôture", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            caisseService.cloturerJour(LocalDate.now(), "—".equals(motif) ? null : motif);
            JOptionPane.showMessageDialog(this, "Clôture validée avec succès.", "Clôture", JOptionPane.INFORMATION_MESSAGE);
            btnValider.setEnabled(false);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
}
