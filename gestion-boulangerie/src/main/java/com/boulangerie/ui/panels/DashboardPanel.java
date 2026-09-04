package com.boulangerie.ui.panels;

import com.boulangerie.dao.*;
import com.boulangerie.model.JournalAudit;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.KpiCard;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tableau de bord Administrateur — Planche 1 écran n°2.
 * Layout responsive : les KPIs et blocs s'étirent avec la fenêtre.
 */
public class DashboardPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame        mainFrame;
    private final FactureDAO       factureDAO   = new FactureDAO();
    private final ClientDAO        clientDAO    = new ClientDAO();
    private final FicheJournaliereDAO ficheDAO  = new FicheJournaliereDAO();
    private final VersementDAO     versementDAO = new VersementDAO();
    private final AuditDAO         auditDAO     = new AuditDAO();

    // KPI cards
    private KpiCard cardCA, cardSorties, cardCreances, cardBloques, cardEcarts;
    // Activité
    private JPanel  pnlActivite;
    // Alertes
    private JPanel  pnlAlertes;

    public DashboardPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(0, 16));
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buildUI();
    }

    private void buildUI() {
        // ── Titre ─────────────────────────────────────────────────
        JLabel lblTitre = new JLabel("Tableau de bord Administrateur");
        lblTitre.setFont(UIConstants.FONT_TITRE);
        lblTitre.setForeground(UIConstants.NOIR_TEXTE);
        add(lblTitre, BorderLayout.NORTH);

        // ── Corps principal ───────────────────────────────────────
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.BOTH;
        gc.insets  = new Insets(0, 0, 12, 0);
        gc.weightx = 1;

        // Ligne 1 : 5 KPI cards (poids égal)
        gc.gridy   = 0; gc.weighty = 0;
        body.add(buildKpiRow(), gc);

        // Ligne 2 : Raccourcis + Activité + Alertes (poids 1 pour remplir)
        gc.gridy   = 1; gc.weighty = 1;
        body.add(buildMiddleRow(), gc);

        add(body, BorderLayout.CENTER);
    }

    // ── Ligne KPI ─────────────────────────────────────────────────
    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 12, 0));
        row.setOpaque(false);
        cardCA       = new KpiCard("CA du jour",        "—", "", UIConstants.BLEU_PRIMAIRE);
        cardSorties  = new KpiCard("Sorties nettes",    "—", "", UIConstants.VERT_SUCCES);
        cardCreances = new KpiCard("Créances en cours", "—", "", UIConstants.ORANGE_ALERTE);
        cardBloques  = new KpiCard("Clients bloqués",   "—", "", UIConstants.ROUGE_DANGER);
        cardEcarts   = new KpiCard("Écarts de caisse",  "—", "", UIConstants.ROUGE_DANGER);
        row.add(cardCA); row.add(cardSorties); row.add(cardCreances);
        row.add(cardBloques); row.add(cardEcarts);
        return row;
    }

    // ── Ligne centrale ────────────────────────────────────────────
    private JPanel buildMiddleRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.add(buildRaccourcisCard());
        row.add(buildActiviteCard());
        row.add(buildAlertesCard());
        return row;
    }

    private JPanel buildRaccourcisCard() {
        JPanel card = buildWhiteCard("Raccourcis");
        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        String[][] items = {
            {"📦", "Produits",    MainFrame.CARD_PRODUITS},
            {"👥", "Clients",     MainFrame.CARD_CLIENTS},
            {"📋", "Sorties",     MainFrame.CARD_SORTIES},
            {"🧾", "Facturation", MainFrame.CARD_FACTURATION},
            {"💰", "Caisse",      MainFrame.CARD_CAISSE},
            {"📊", "Recouvrement",MainFrame.CARD_RECOUVREMENT}
        };
        for (String[] it : items) {
            JButton btn = new JButton(
                "<html><center><span style='font-size:18px'>" + it[0] + "</span>"
                + "<br><small>" + it[1] + "</small></center></html>");
            btn.setBackground(UIConstants.BLEU_CLAIR);
            btn.setForeground(UIConstants.BLEU_PRIMAIRE);
            btn.setFont(UIConstants.FONT_NORMAL);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> mainFrame.showPanel(it[2]));
            grid.add(btn);
        }
        JPanel inner = getInnerPanel(card);
        if (inner != null) inner.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildActiviteCard() {
        JPanel card = buildWhiteCard("Activité récente");
        pnlActivite = new JPanel();
        pnlActivite.setLayout(new BoxLayout(pnlActivite, BoxLayout.Y_AXIS));
        pnlActivite.setOpaque(false);
        JScrollPane scroll = new JScrollPane(pnlActivite);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JPanel inner = getInnerPanel(card);
        if (inner != null) inner.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAlertesCard() {
        JPanel card = buildWhiteCard("Alertes");
        pnlAlertes = new JPanel();
        pnlAlertes.setLayout(new BoxLayout(pnlAlertes, BoxLayout.Y_AXIS));
        pnlAlertes.setOpaque(false);
        JScrollPane scroll = new JScrollPane(pnlAlertes);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        RoundedButton btnVoirTout = new RoundedButton("Voir toutes les alertes", RoundedButton.Style.OUTLINE);
        btnVoirTout.addActionListener(e -> mainFrame.showPanel(MainFrame.CARD_CLIENTS));

        JPanel inner = getInnerPanel(card);
        if (inner != null) {
            inner.add(scroll,     BorderLayout.CENTER);
            inner.add(btnVoirTout,BorderLayout.SOUTH);
        }
        return card;
    }

    // ── Carte blanche réutilisable ────────────────────────────────
    private JPanel buildWhiteCard(String titre) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JLabel lbl = new JLabel(titre);
        lbl.setFont(UIConstants.FONT_SOUS_TITRE);
        lbl.setForeground(UIConstants.NOIR_TEXTE);
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GRIS_BORDURE));
        card.add(lbl, BorderLayout.NORTH);

        JPanel inner = new JPanel(new BorderLayout(0, 6));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        card.add(inner, BorderLayout.CENTER);
        card.putClientProperty("inner", inner);
        return card;
    }

    private JPanel getInnerPanel(JPanel card) {
        return (JPanel) card.getClientProperty("inner");
    }

    // ── Refresh données ───────────────────────────────────────────
    @Override
    public void refresh() {
        SwingWorker<DashboardData, Void> worker = new SwingWorker<>() {
            @Override protected DashboardData doInBackground() {
                LocalDate today = LocalDate.now();
                DashboardData d = new DashboardData();
                d.caJour        = factureDAO.getCaJour(today);
                d.sortiesNettes = ficheDAO.getSortiesNettesJour(today);
                d.creances      = factureDAO.getCreancesEnCours();
                d.bloques       = clientDAO.countBloques();
                d.ecarts        = versementDAO.getEcartsCaisseJour(today);
                d.activites     = auditDAO.search(null, null, null, today, today, 8, 0);
                return d;
            }
            @Override protected void done() {
                try {
                    DashboardData d = get();
                    cardCA.setValeur(FormatUtil.montant(d.caJour) + " FCFA");
                    cardSorties.setValeur(FormatUtil.montant(d.sortiesNettes) + " FCFA");
                    cardCreances.setValeur(FormatUtil.montant(d.creances) + " FCFA");
                    cardBloques.setValeur(d.bloques + " client(s)");
                    cardEcarts.setValeur(FormatUtil.montant(d.ecarts) + " FCFA");
                    cardEcarts.setVariation(
                        d.ecarts.compareTo(BigDecimal.ZERO) < 0 ? "⚠ Ajuster aujourd'hui" : "✓ OK");
                    refreshActivites(d.activites);
                    refreshAlertes(d);
                } catch (Exception ignore) {}
            }
        };
        worker.execute();
    }

    private void refreshActivites(List<JournalAudit> list) {
        pnlActivite.removeAll();
        for (JournalAudit a : list) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GRIS_FOND));

            String heureStr = a.getDateAction() != null
                ? a.getDateAction().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
            String details  = a.getDetails() != null && a.getDetails().length() > 45
                ? a.getDetails().substring(0, 45) + "…" : (a.getDetails() != null ? a.getDetails() : "");

            JLabel lblAction = new JLabel(a.getEntite() + "  —  " + a.getAction());
            lblAction.setFont(UIConstants.FONT_BOLD);
            JLabel lblDet = new JLabel(details);
            lblDet.setFont(UIConstants.FONT_PETIT);
            lblDet.setForeground(UIConstants.GRIS_TEXTE);
            JLabel lblH = new JLabel(heureStr);
            lblH.setFont(UIConstants.FONT_PETIT);
            lblH.setForeground(UIConstants.GRIS_TEXTE);

            JPanel textes = new JPanel(new BorderLayout(0, 1));
            textes.setOpaque(false);
            textes.add(lblAction, BorderLayout.NORTH);
            textes.add(lblDet,    BorderLayout.CENTER);
            row.add(textes, BorderLayout.CENTER);
            row.add(lblH,   BorderLayout.EAST);
            pnlActivite.add(row);
            pnlActivite.add(Box.createVerticalStrut(2));
        }
        pnlActivite.revalidate();
        pnlActivite.repaint();
    }

    private void refreshAlertes(DashboardData d) {
        pnlAlertes.removeAll();
        if (d.bloques > 0) {
            pnlAlertes.add(buildAlerteItem(
                UIConstants.ROUGE_DANGER,
                "🔴  " + d.bloques + " client(s) bloqué(s)",
                "Voir la liste",
                MainFrame.CARD_CLIENTS));
        }
        if (d.creances.compareTo(BigDecimal.ZERO) > 0) {
            pnlAlertes.add(buildAlerteItem(
                UIConstants.ORANGE_ALERTE,
                "🟡  Créances en cours : " + FormatUtil.montant(d.creances) + " FCFA",
                "Voir le détail",
                MainFrame.CARD_FACTURATION));
        }
        if (d.ecarts.compareTo(BigDecimal.ZERO) < 0) {
            pnlAlertes.add(buildAlerteItem(
                UIConstants.ROUGE_DANGER,
                "🔴  Écart de caisse : " + FormatUtil.montant(d.ecarts) + " FCFA",
                "Rapprochement",
                MainFrame.CARD_RECOUVREMENT));
        }
        if (pnlAlertes.getComponentCount() == 0) {
            JLabel ok = new JLabel("✅  Aucune alerte active");
            ok.setFont(UIConstants.FONT_NORMAL);
            ok.setForeground(UIConstants.VERT_SUCCES);
            pnlAlertes.add(ok);
        }
        pnlAlertes.revalidate();
        pnlAlertes.repaint();
    }

    private JPanel buildAlerteItem(Color color, String msg, String lienLabel, String card) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, color),
            BorderFactory.createEmptyBorder(4, 8, 4, 0)));
        JLabel lblMsg = new JLabel(msg);
        lblMsg.setFont(UIConstants.FONT_NORMAL);
        JButton btnLien = new JButton(lienLabel);
        btnLien.setFont(UIConstants.FONT_PETIT);
        btnLien.setForeground(UIConstants.BLEU_PRIMAIRE);
        btnLien.setBorderPainted(false);
        btnLien.setContentAreaFilled(false);
        btnLien.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLien.addActionListener(e -> mainFrame.showPanel(card));
        p.add(lblMsg,  BorderLayout.CENTER);
        p.add(btnLien, BorderLayout.EAST);
        return p;
    }

    private static class DashboardData {
        BigDecimal caJour, sortiesNettes, creances, ecarts;
        int bloques;
        List<JournalAudit> activites;
    }
}
