package com.boulangerie.ui.panels;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import com.boulangerie.service.*;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class SortiesPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame mainFrame;
    private final FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
    private final FacturationService factService = new FacturationService();
    private final SessionService session = SessionService.getInstance();

    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private List<FicheJournaliere> fiches;
    private JLabel lblTotals;

    public SortiesPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = {"N° fiche","Date","Livreur","État","Nb lignes",
                         "Total sorties (HT)","Total retours (HT)","Total net (HT)","Action"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(3).setCellRenderer((t,v,s,f,r,c) ->
            StatusBadge.forStatut(v != null ? v.toString() : ""));
        buildUI();
        refresh();
    }

    private void buildUI() {
        // ── Titre + Toolbar ───────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lbl = new JLabel("Fiches journalières");
        lbl.setFont(UIConstants.FONT_TITRE);
        header.add(lbl, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        JLabel lblDate = new JLabel("Fiches du jour");
        lblDate.setFont(UIConstants.FONT_SOUS_TITRE);

        RoundedButton btnNouv   = new RoundedButton("+ Nouvelle fiche", RoundedButton.Style.PRIMARY);
        RoundedButton btnOuvrir = new RoundedButton("Ouvrir",           RoundedButton.Style.OUTLINE);
        RoundedButton btnClot   = new RoundedButton("Clôturer",         RoundedButton.Style.SECONDARY);

        toolbar.add(lblDate);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(btnNouv); toolbar.add(btnOuvrir); toolbar.add(btnClot);
        header.add(toolbar, BorderLayout.SOUTH);

        // Actions
        btnNouv.addActionListener(e -> nouvelleFiche());
        btnOuvrir.addActionListener(e -> ouvrirFicheSelectionnee());
        btnClot.addActionListener(e -> cloturerFicheSelectionnee());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) ouvrirFicheSelectionnee();
            }
        });

        // ── Table ─────────────────────────────────────────────────
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        // ── Footer totaux ─────────────────────────────────────────
        lblTotals = new JLabel("Total fiches : 0");
        lblTotals.setFont(UIConstants.FONT_PETIT);
        lblTotals.setForeground(UIConstants.GRIS_TEXTE);
        lblTotals.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
        add(lblTotals, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        SwingWorker<List<FicheJournaliere>, Void> w = new SwingWorker<>() {
            @Override protected List<FicheJournaliere> doInBackground() {
                return ficheDAO.findByDate(LocalDate.now());
            }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void majTable(List<FicheJournaliere> list) {
        this.fiches = list;
        tableModel.setRowCount(0);
        java.math.BigDecimal totSorties = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totRetours = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totNet     = java.math.BigDecimal.ZERO;
        for (FicheJournaliere f : list) {
            tableModel.addRow(new Object[]{
                f.getNumero(),
                FormatUtil.date(f.getDateFiche()),
                f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—",
                f.getStatut().name(),
                f.getNbLignes(),
                FormatUtil.montant(f.getTotalSorties()),
                FormatUtil.montant(f.getTotalRetours()),
                FormatUtil.montant(f.getTotalNet()),
                "Ouvrir"
            });
            totSorties = totSorties.add(f.getTotalSorties());
            totRetours = totRetours.add(f.getTotalRetours());
            totNet     = totNet.add(f.getTotalNet());
        }
        lblTotals.setText("Total fiches : " + list.size()
            + "   |   Sorties : " + FormatUtil.montant(totSorties)
            + "   |   Retours : " + FormatUtil.montant(totRetours)
            + "   |   Net : " + FormatUtil.montant(totNet));
        table.autoResizeColumns();
    }

    private void nouvelleFiche() {
        // Choisir le livreur
        UtilisateurDAO uDAO = new UtilisateurDAO();
        List<Utilisateur> livreurs = uDAO.findLivreurs();
        if (livreurs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucun livreur disponible.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Utilisateur[] arr = livreurs.toArray(new Utilisateur[0]);
        Utilisateur livreur = (Utilisateur) JOptionPane.showInputDialog(this,
            "Sélectionner le livreur :", "Nouvelle fiche", JOptionPane.QUESTION_MESSAGE,
            null, arr, arr[0]);
        if (livreur == null) return;

        FicheJournaliere f = new FicheJournaliere();
        f.setDateFiche(LocalDate.now());
        f.setLivreur(livreur);
        f.setNumero(ficheDAO.genererNumero(LocalDate.now()));
        f.setStatut(FicheJournaliere.Statut.EnCours);
        f.setCreePar(session.getUserId());
        String id = ficheDAO.save(f);
        f.setId(id);

        new AuditDAO().log(new JournalAudit("FicheJournaliere", id, JournalAudit.CREATE,
            session.getUserId(), session.getLogin(), "Nouvelle fiche: " + f.getNumero()));

        ouvrirSaisieFiche(f);
    }

    private void ouvrirFicheSelectionnee() {
        int row = table.getSelectedRow();
        if (row < 0 || fiches == null || row >= fiches.size()) return;
        FicheJournaliere f = fiches.get(row);
        ficheDAO.findById(f.getId()).ifPresent(this::ouvrirSaisieFiche);
    }

    private void ouvrirSaisieFiche(FicheJournaliere fiche) {
        com.boulangerie.ui.dialogs.SaisieDialog dlg = new com.boulangerie.ui.dialogs.SaisieDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), fiche, factService);
        dlg.setVisible(true);
        refresh();
    }

    private void cloturerFicheSelectionnee() {
        int row = table.getSelectedRow();
        if (row < 0 || fiches == null || row >= fiches.size()) return;
        FicheJournaliere f = fiches.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Clôturer la fiche " + f.getNumero() + " ? Cette action générera les factures.", "Confirmation",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        ficheDAO.findById(f.getId()).ifPresent(fiche -> {
            List<Facture> factures = factService.genererDepuisFiche(fiche);
            ficheDAO.updateStatut(fiche.getId(), FicheJournaliere.Statut.Clôturée);
            JOptionPane.showMessageDialog(this,
                factures.size() + " facture(s) générée(s) automatiquement.",
                "Clôture réussie", JOptionPane.INFORMATION_MESSAGE);
            refresh();
        });
    }
}
