package com.boulangerie.ui.panels;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.PdfService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class RapportsPanel extends JPanel implements MainFrame.Refreshable {

    private final FactureDAO   factureDAO   = new FactureDAO();
    private final ClientDAO    clientDAO    = new ClientDAO();
    private final VersementDAO versementDAO = new VersementDAO();

    public RapportsPanel(MainFrame mainFrame) {
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel lbl = new JLabel("Rapports & Exports");
        lbl.setFont(UIConstants.FONT_TITRE);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JPanel cardsPanel = new JPanel(new GridLayout(2, 3, 16, 16));
        cardsPanel.setOpaque(false);

        cardsPanel.add(buildRapportCard("📋 État journalier",
            "Fiches sorties/retours du jour avec totaux par livreur.",
            e -> genererEtatJournalier()));
        cardsPanel.add(buildRapportCard("🧾 Factures du mois",
            "Liste des factures émises sur la période sélectionnée.",
            e -> genererFacturesMois()));
        cardsPanel.add(buildRapportCard("💰 Recouvrement mensuel",
            "Objectif vs réalisé, taux de recouvrement par client.",
            e -> genererRecouvrementMensuel()));
        cardsPanel.add(buildRapportCard("👥 Soldes clients",
            "État des soldes de tous les clients nominatifs.",
            e -> genererSoldesClients()));
        cardsPanel.add(buildRapportCard("📦 Analyse produits",
            "Volumes vendus par produit et par famille.",
            e -> genererAnalyseProduits()));
        cardsPanel.add(buildRapportCard("🔍 Journal d'audit",
            "Exporter le journal d'audit sur une période.",
            e -> exportAudit()));

        add(lbl, BorderLayout.NORTH);
        add(cardsPanel, BorderLayout.CENTER);

        JLabel lblNote = new JLabel("Tous les rapports sont générés au format PDF (A4) avec aperçu avant impression.");
        lblNote.setFont(UIConstants.FONT_PETIT);
        lblNote.setForeground(UIConstants.GRIS_TEXTE);
        lblNote.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        add(lblNote, BorderLayout.SOUTH);
    }

    private JPanel buildRapportCard(String titre, String desc, java.awt.event.ActionListener action) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JLabel lblT = new JLabel(titre);
        lblT.setFont(UIConstants.FONT_SOUS_TITRE);
        JLabel lblD = new JLabel("<html>" + desc + "</html>");
        lblD.setFont(UIConstants.FONT_PETIT);
        lblD.setForeground(UIConstants.GRIS_TEXTE);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btnRow.setOpaque(false);
        RoundedButton btnPDF     = new RoundedButton("Export PDF", RoundedButton.Style.PRIMARY);
        RoundedButton btnAperçu  = new RoundedButton("Aperçu",     RoundedButton.Style.OUTLINE);
        btnPDF.addActionListener(action);
        btnAperçu.addActionListener(action);
        btnRow.add(btnPDF); btnRow.add(btnAperçu);

        p.add(lblT,   BorderLayout.NORTH);
        p.add(lblD,   BorderLayout.CENTER);
        p.add(btnRow, BorderLayout.SOUTH);
        return p;
    }

    private void genererEtatJournalier() {
        choisirFichierEtExporter("EtatJournalier_" + LocalDate.now() + ".pdf", file -> {
            List<FicheJournaliere> fiches = new FicheJournaliereDAO().findByDate(LocalDate.now());
            PdfService.exporterEtatJournalier(fiches, file.getAbsolutePath());
        });
    }

    private void genererFacturesMois() {
        LocalDate debut = LocalDate.now().withDayOfMonth(1);
        LocalDate fin   = LocalDate.now();
        choisirFichierEtExporter("Factures_" + debut.getYear() + "_" + debut.getMonthValue() + ".pdf", file -> {
            List<Facture> factures = factureDAO.findByFilters(debut, fin, null, null);
            PdfService.exporterListeFactures(factures, file.getAbsolutePath());
        });
    }

    private void genererRecouvrementMensuel() {
        choisirFichierEtExporter("Recouvrement_" + LocalDate.now() + ".pdf", file -> {
            PdfService.exporterRecouvrement(versementDAO, LocalDate.now().withDayOfMonth(1),
                LocalDate.now(), file.getAbsolutePath());
        });
    }

    private void genererSoldesClients() {
        choisirFichierEtExporter("SoldesClients_" + LocalDate.now() + ".pdf", file -> {
            List<Client> clients = clientDAO.findAll();
            PdfService.exporterSoldesClients(clients, file.getAbsolutePath());
        });
    }

    private void genererAnalyseProduits() {
        JOptionPane.showMessageDialog(this, "Rapport Analyse Produits en cours de développement.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportAudit() {
        choisirFichierEtExporter("Audit_" + LocalDate.now() + ".pdf", file -> {
            List<JournalAudit> audits = new AuditDAO().search(null, null, null,
                LocalDate.now().minusDays(30), LocalDate.now(), 500, 0);
            PdfService.exporterAudit(audits, file.getAbsolutePath());
        });
    }

    private void choisirFichierEtExporter(String nomDefaut, ExportAction action) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(nomDefaut));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            action.export(fc.getSelectedFile());
            JOptionPane.showMessageDialog(this, "PDF exporté : " + fc.getSelectedFile().getName(), "Export réussi", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur export: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface interface ExportAction { void export(File f) throws Exception; }

    @Override public void refresh() {}
}
