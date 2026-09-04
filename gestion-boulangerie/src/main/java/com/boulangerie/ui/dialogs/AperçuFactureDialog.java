package com.boulangerie.ui.dialogs;

import com.boulangerie.dao.FicheJournaliereDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.PdfService;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.print.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aperçu avant impression d'une facture.
 * Reproduit l'écran n°10 de la Planche 2.
 */
public class AperçuFactureDialog extends JDialog {

    private final Facture facture;
    private List<LigneSortie> lignes;

    public AperçuFactureDialog(Frame parent, Facture facture) {
        super(parent, "Facture — Aperçu avant impression", true);
        this.facture = facture;
        setSize(760, 680);
        setLocationRelativeTo(parent);
        // Charger les lignes depuis la fiche
        if (facture.getFicheId() != null) {
            FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
            ficheDAO.findById(facture.getFicheId()).ifPresent(f -> lignes = f.getLignes());
        }
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(UIConstants.GRIS_FOND);

        // ── Panneau aperçu (simulé) ───────────────────────────────
        JPanel preview = buildFacturePreview();
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));

        // ── Barre latérale (zoom) ─────────────────────────────────
        JToolBar sidebar = new JToolBar(JToolBar.VERTICAL);
        sidebar.setFloatable(false);
        sidebar.setBackground(UIConstants.GRIS_FOND);
        for (String icon : new String[]{"🔍+", "🔍−", "📄", "⬇", "⬆", "🖨"}) {
            JButton b = new JButton(icon);
            b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            b.setBorderPainted(false); b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            sidebar.add(b);
        }
        add(scroll,  BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);

        // ── Footer ────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        footer.setBackground(UIConstants.GRIS_FOND);
        JButton btnPrint = new JButton("🖨 Imprimer");
        btnPrint.setFont(UIConstants.FONT_NORMAL);
        btnPrint.addActionListener(e -> imprimer());
        JButton btnPDF = new JButton("📄 Export PDF");
        btnPDF.setFont(UIConstants.FONT_NORMAL);
        btnPDF.addActionListener(e -> exporterPDF());
        footer.add(btnPrint); footer.add(btnPDF);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildFacturePreview() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));

        // En-tête
        JLabel lblEntreprise = new JLabel("🥖  BOULANGERIE");
        lblEntreprise.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblEntreprise.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblFacture = new JLabel("FACTURE");
        lblFacture.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFacture.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblNum = new JLabel("N° " + facture.getNumero());
        lblNum.setFont(UIConstants.FONT_NORMAL);
        lblNum.setAlignmentX(CENTER_ALIGNMENT);

        // Client
        String clientNom = facture.getClient() != null ? facture.getClient().getNom() : "Client anonyme";
        JLabel lblClientTitre = new JLabel("Client : " + clientNom);
        lblClientTitre.setFont(UIConstants.FONT_BOLD);
        JLabel lblDate = new JLabel("Date : " + FormatUtil.date(facture.getDateEmission()));
        JLabel lblLivreur = new JLabel("Livreur : " + (facture.getLivreur() != null ? facture.getLivreur().getNomComplet() : "—"));
        JLabel lblMode = new JLabel("Mode de règlement : " + (facture.getModeReglement() != null ? facture.getModeReglement() : "Comptant"));

        // Table des lignes (simulée)
        String[] cols = {"Désignation", "Quantité nette", "Tarif unitaire (HT)", "Montant (HT)"};
        Object[][] rows = {};
        if (lignes != null) {
            rows = lignes.stream()
                .filter(l -> facture.getClient() != null && l.getClient() != null
                    && facture.getClient().getId().equals(l.getClient().getId()))
                .map(l -> new Object[]{
                    l.getProduit() != null ? l.getProduit().getLibelle() : "—",
                    l.getQuantiteNette(),
                    FormatUtil.montant(l.getTarifApplicable()),
                    FormatUtil.montant(l.getMontantHt())
                }).toArray(Object[][]::new);
        }
        JTable lignesTable = new JTable(rows, cols);
        lignesTable.setFont(UIConstants.FONT_NORMAL);
        lignesTable.setRowHeight(26);
        lignesTable.setEnabled(false);
        JScrollPane tableScroll = new JScrollPane(lignesTable);
        tableScroll.setPreferredSize(new Dimension(600, 180));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        // Totaux
        JPanel totaux = new JPanel(new GridLayout(4, 2, 8, 4));
        totaux.setOpaque(false);
        totaux.setMaximumSize(new Dimension(400, 120));
        totaux.setAlignmentX(RIGHT_ALIGNMENT);
        totaux.add(new JLabel("Total HT")); totaux.add(new JLabel(FormatUtil.montant(facture.getMontantHt()), SwingConstants.RIGHT));
        totaux.add(new JLabel("TVA (" + facture.getTvaPct() + "%)")); totaux.add(new JLabel(FormatUtil.montant(facture.getTvaMontant()), SwingConstants.RIGHT));
        JLabel lblTTC = new JLabel("Total TTC"); lblTTC.setFont(UIConstants.FONT_BOLD);
        JLabel lblTTCVal = new JLabel(FormatUtil.montant(facture.getMontantTtc()), SwingConstants.RIGHT); lblTTCVal.setFont(UIConstants.FONT_BOLD);
        totaux.add(lblTTC); totaux.add(lblTTCVal);

        // Pied de page
        JLabel lblPied = new JLabel("Période : " + FormatUtil.date(facture.getDateEmission())
            + " — Généré le : " + FormatUtil.dateHeure(LocalDateTime.now()) + " — Page 1 / 1");
        lblPied.setFont(UIConstants.FONT_PETIT);
        lblPied.setForeground(UIConstants.GRIS_TEXTE);
        lblPied.setAlignmentX(CENTER_ALIGNMENT);

        // Assemblage
        p.add(lblEntreprise);
        p.add(Box.createVerticalStrut(4));
        p.add(lblFacture);
        p.add(Box.createVerticalStrut(2));
        p.add(lblNum);
        p.add(Box.createVerticalStrut(12));
        p.add(new JSeparator());
        p.add(Box.createVerticalStrut(8));
        p.add(lblClientTitre); p.add(lblDate); p.add(lblLivreur); p.add(lblMode);
        p.add(Box.createVerticalStrut(12));
        p.add(tableScroll);
        p.add(Box.createVerticalStrut(12));
        p.add(totaux);
        p.add(Box.createVerticalStrut(20));
        p.add(new JSeparator());
        p.add(Box.createVerticalStrut(6));
        p.add(lblPied);
        return p;
    }

    private void imprimer() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Facture " + facture.getNumero());
        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Erreur impression: " + ex.getMessage());
            }
        }
    }

    private void exporterPDF() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Facture_" + facture.getNumero() + ".pdf"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                PdfService.exporterFacture(facture, lignes, fc.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this, "PDF exporté avec succès.", "Export PDF", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur export PDF: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
