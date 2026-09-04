package com.boulangerie.ui.panels;

import com.boulangerie.dao.FactureDAO;
import com.boulangerie.model.Facture;
import com.boulangerie.service.FacturationService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.ui.dialogs.AperçuFactureDialog;
import com.boulangerie.ui.dialogs.AvoirDialog;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class FacturationPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame mainFrame;
    private final FactureDAO factureDAO = new FactureDAO();
    private final FacturationService factService = new FacturationService();
    private final SessionService session = SessionService.getInstance();

    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private List<Facture> factures;

    public FacturationPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = {"N° facture","Date","Client","Livreur","Montant HT","TVA","TTC","Statut facture","Statut paiement","Action"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        // Badge statut
        table.getColumnModel().getColumn(7).setCellRenderer((t,v,s,f,r,c) ->
            StatusBadge.forStatut(v != null ? v.toString() : ""));
        table.getColumnModel().getColumn(8).setCellRenderer((t,v,s,f,r,c) ->
            StatusBadge.forStatut(v != null ? v.toString() : ""));

        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lbl = new JLabel("Facturation");
        lbl.setFont(UIConstants.FONT_TITRE);
        header.add(lbl, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        RoundedButton btnGenAuto = new RoundedButton("Génération auto depuis sorties nettes", RoundedButton.Style.PRIMARY);
        RoundedButton btnAvoir   = new RoundedButton("Créer un avoir",   RoundedButton.Style.DANGER);
        RoundedButton btnRefresh = new RoundedButton("⟳", RoundedButton.Style.OUTLINE);
        toolbar.add(btnGenAuto); toolbar.add(btnAvoir); toolbar.add(btnRefresh);

        btnRefresh.addActionListener(e -> refresh());
        btnAvoir.addActionListener(e -> creerAvoir());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) aperçuFacture();
            }
        });

        // Note verrouillée
        JLabel lblVerrou = new JLabel("🔒 Facture verrouillée non modifiable");
        lblVerrou.setFont(UIConstants.FONT_PETIT);
        lblVerrou.setForeground(UIConstants.ROUGE_DANGER);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(lblVerrou);

        header.add(toolbar, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        JLabel lblFooter = new JLabel("Affichage 0 sur 0 factures");
        lblFooter.setFont(UIConstants.FONT_PETIT);
        lblFooter.setForeground(UIConstants.GRIS_TEXTE);

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
        add(lblFooter, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        SwingWorker<List<Facture>, Void> w = new SwingWorker<>() {
            @Override protected List<Facture> doInBackground() { return factureDAO.findAll(); }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void majTable(List<Facture> list) {
        this.factures = list;
        tableModel.setRowCount(0);
        for (Facture f : list) {
            tableModel.addRow(new Object[]{
                f.getNumero(),
                FormatUtil.date(f.getDateEmission()),
                f.getClient() != null ? f.getClient().getNom() : "Client anonyme",
                f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—",
                FormatUtil.montant(f.getMontantHt()),
                FormatUtil.montant(f.getTvaMontant()),
                FormatUtil.montant(f.getMontantTtc()),
                f.isEstVerrouillee() ? "Verrouillée" : "Ouverte",
                f.getStatut().name(),
                "..."
            });
        }
        table.autoResizeColumns();
    }

    private void creerAvoir() {
        int row = table.getSelectedRow();
        if (row < 0 || factures == null || row >= factures.size()) {
            JOptionPane.showMessageDialog(this, "Sélectionnez une facture.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Facture f = factures.get(row);
        if (!f.isEstVerrouillee()) {
            JOptionPane.showMessageDialog(this, "La facture doit être verrouillée pour créer un avoir.", "Info", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!session.hasPermission("AVOIR_WRITE")) {
            JOptionPane.showMessageDialog(this, "Permission refusée.", "Accès", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Charger la facture complète
        factureDAO.findById(f.getId()).ifPresent(facture -> {
            AvoirDialog dlg = new AvoirDialog((Frame) SwingUtilities.getWindowAncestor(this), facture);
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh();
        });
    }

    private void aperçuFacture() {
        int row = table.getSelectedRow();
        if (row < 0 || factures == null || row >= factures.size()) return;
        Facture f = factures.get(row);
        factureDAO.findById(f.getId()).ifPresent(facture -> {
            AperçuFactureDialog dlg = new AperçuFactureDialog((Frame) SwingUtilities.getWindowAncestor(this), facture);
            dlg.setVisible(true);
        });
    }
}
