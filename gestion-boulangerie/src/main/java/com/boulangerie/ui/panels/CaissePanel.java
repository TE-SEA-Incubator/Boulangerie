package com.boulangerie.ui.panels;

import com.boulangerie.dao.FactureDAO;
import com.boulangerie.dao.UtilisateurDAO;
import com.boulangerie.dao.VersementDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.CaisseService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.ui.dialogs.VersementRecuDialog;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Module Caisse — Versements & Reçus.
 * Reproduit l'écran 11 de la Planche 2.
 */
public class CaissePanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame      mainFrame;
    private final VersementDAO   versementDAO  = new VersementDAO();
    private final FactureDAO     factureDAO    = new FactureDAO();
    private final SessionService session       = SessionService.getInstance();

    private final DefaultTableModel versementsModel;
    private final StyledTable       versementsTable;
    private List<Versement>         versements;

    private JLabel lblTotalAttendu, lblTotalRemis, lblTotalEcart;

    public CaissePanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        String[] cols = {
            "N° reçu", "Livreur", "Facture",
            "Attendu (TTC)", "Montant remis", "Montant enregistré", "Écart", "Statut"
        };
        versementsModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        versementsTable = new StyledTable(versementsModel);
        versementsTable.getColumnModel().getColumn(7).setCellRenderer(
            (t, v, s, f, r, c) -> StatusBadge.forStatut(v != null ? v.toString() : ""));

        buildUI();
        refresh();
    }

    private void buildUI() {
        // ── Titre + Toolbar ───────────────────────────────────────
        JLabel lbl = new JLabel("Caisse — Versements & Reçus");
        lbl.setFont(UIConstants.FONT_TITRE);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);

        RoundedButton btnNouveau  = new RoundedButton("+ Nouveau versement", RoundedButton.Style.PRIMARY);
        RoundedButton btnApercu   = new RoundedButton("Voir reçu",           RoundedButton.Style.OUTLINE);
        RoundedButton btnRefresh  = new RoundedButton("⟳",                  RoundedButton.Style.OUTLINE);

        toolbar.add(btnNouveau); toolbar.add(btnApercu); toolbar.add(btnRefresh);

        btnNouveau.addActionListener(e -> ouvrirVersementDialog(null));
        btnApercu.addActionListener(e  -> ouvrirVersementSelectionne());
        btnRefresh.addActionListener(e -> refresh());

        versementsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) ouvrirVersementSelectionne();
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(lbl,     BorderLayout.WEST);
        header.add(toolbar, BorderLayout.SOUTH);

        // ── Table versements ──────────────────────────────────────
        JScrollPane scroll = new JScrollPane(versementsTable);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        // ── Barre de totaux ───────────────────────────────────────
        JPanel totauxBar = buildTotauxBar();

        add(header,    BorderLayout.NORTH);
        add(scroll,    BorderLayout.CENTER);
        add(totauxBar, BorderLayout.SOUTH);
    }

    private JPanel buildTotauxBar() {
        JPanel p = new JPanel(new GridLayout(1, 4, 12, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        lblTotalAttendu = kpiLabel("Total attendu (TTC)", "0,00 FCFA", UIConstants.GRIS_TEXTE);
        lblTotalRemis   = kpiLabel("Total remis (TTC)",   "0,00 FCFA", UIConstants.BLEU_PRIMAIRE);
        lblTotalEcart   = kpiLabel("Écart total",         "0,00 FCFA", UIConstants.ROUGE_DANGER);

        p.add(lblTotalAttendu);
        p.add(lblTotalRemis);
        p.add(lblTotalEcart);

        // Bouton clôture rapide
        RoundedButton btnClot = new RoundedButton("🔒 Clôturer la caisse", RoundedButton.Style.DANGER);
        btnClot.addActionListener(e -> mainFrame.showPanel(MainFrame.CARD_RECOUVREMENT));
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        btnWrap.setOpaque(false);
        btnWrap.add(btnClot);
        p.add(btnWrap);
        return p;
    }

    private JLabel kpiLabel(String titre, String val, java.awt.Color color) {
        JLabel lbl = new JLabel(
            "<html><small style='color:#5F6368'>" + titre + "</small><br>"
            + "<b>" + val + "</b></html>");
        lbl.setForeground(color);
        lbl.setBackground(Color.WHITE);
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return lbl;
    }

    @Override
    public void refresh() {
        SwingWorker<List<Versement>, Void> w = new SwingWorker<>() {
            @Override protected List<Versement> doInBackground() {
                return versementDAO.findByDate(LocalDate.now());
            }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void majTable(List<Versement> list) {
        this.versements = list;
        versementsModel.setRowCount(0);
        BigDecimal totalAttendu = BigDecimal.ZERO;
        BigDecimal totalRemis   = BigDecimal.ZERO;
        BigDecimal totalEcart   = BigDecimal.ZERO;

        for (Versement v : list) {
            versementsModel.addRow(new Object[]{
                v.getNumero(),
                v.getLivreur()  != null ? v.getLivreur().getNomComplet()  : "—",
                v.getFacture()  != null ? v.getFacture().getNumero()      : "—",
                FormatUtil.montant(v.getMontantAttendu()),
                FormatUtil.montant(v.getMontantRemis()),
                FormatUtil.montant(v.getMontantEnregistre()),
                FormatUtil.montant(v.getEcart()),
                v.getStatut().name()
            });
            totalAttendu = totalAttendu.add(v.getMontantAttendu());
            totalRemis   = totalRemis.add(v.getMontantRemis());
            totalEcart   = totalEcart.add(v.getEcart());
        }
        versementsTable.autoResizeColumns();

        // Mettre à jour les KPIs
        final BigDecimal fa = totalAttendu, fr = totalRemis, fe = totalEcart;
        SwingUtilities.invokeLater(() -> {
            lblTotalAttendu.setText(
                "<html><small style='color:#5F6368'>Total attendu (TTC)</small><br>"
                + "<b>" + FormatUtil.montant(fa) + " FCFA</b></html>");
            lblTotalRemis.setText(
                "<html><small style='color:#5F6368'>Total remis (TTC)</small><br>"
                + "<b>" + FormatUtil.montant(fr) + " FCFA</b></html>");
            lblTotalEcart.setText(
                "<html><small style='color:#5F6368'>Écart total</small><br>"
                + "<b style='color:" + (fe.compareTo(BigDecimal.ZERO) == 0 ? "#0F9D58" : "#D93025") + "'>"
                + FormatUtil.montant(fe) + " FCFA</b></html>");
        });
    }

    private void ouvrirVersementDialog(Facture facture) {
        VersementRecuDialog dlg = new VersementRecuDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), facture);
        dlg.setVisible(true);
        if (dlg.isSaved()) refresh();
    }

    private void ouvrirVersementSelectionne() {
        int row = versementsTable.getSelectedRow();
        if (row < 0 || versements == null || row >= versements.size()) {
            ouvrirVersementDialog(null);
            return;
        }
        Versement v = versements.get(row);
        if (v.getFacture() != null) {
            factureDAO.findById(v.getFacture().getId())
                .ifPresent(f -> ouvrirVersementDialog(f));
        }
    }
}
