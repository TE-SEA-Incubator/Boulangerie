package com.boulangerie.ui.panels;

import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.model.*;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.ui.dialogs.ProduitDialog;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class ProduitsPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame mainFrame;
    private final ProduitDAO produitDAO = new ProduitDAO();
    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private final SearchField searchField = new SearchField("Rechercher un produit...");
    private JComboBox<Famille> cboFamille;
    private JCheckBox chkInactifs;
    private List<Produit> produits;

    public ProduitsPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Modèle de table
        String[] cols = {"Code", "Libellé", "Famille", "Unité", "Statut", "Seuil alerte"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(4).setCellRenderer(new BadgeRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        buildUI();
        refresh();
    }

    private void buildUI() {
        // ── Barre d'outils ────────────────────────────────────────
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);

        RoundedButton btnNouveau  = new RoundedButton("+ Nouveau", RoundedButton.Style.PRIMARY);
        RoundedButton btnImprimer = new RoundedButton("Imprimer", RoundedButton.Style.SECONDARY);
        RoundedButton btnExport   = new RoundedButton("Export PDF", RoundedButton.Style.SECONDARY);

        // Famille filter
        List<Famille> familles = produitDAO.findAllFamilles();
        cboFamille = new JComboBox<>();
        cboFamille.addItem(new Famille("", "Toutes"));
        familles.forEach(cboFamille::addItem);
        cboFamille.setPreferredSize(new Dimension(140, 32));

        chkInactifs = new JCheckBox("Afficher inactifs");
        chkInactifs.setOpaque(false);
        chkInactifs.setFont(UIConstants.FONT_NORMAL);

        toolbar.add(btnNouveau);
        toolbar.add(btnImprimer);
        toolbar.add(btnExport);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(new JLabel("Famille :"));
        toolbar.add(cboFamille);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(new JLabel("Statut :"));
        toolbar.add(new JComboBox<>(new String[]{"Tous", "Actif", "Inactif"}));
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(searchField);
        toolbar.add(chkInactifs);

        // Actions
        btnNouveau.addActionListener(e -> openDialog(null));
        searchField.addActionListener(e -> appliquerFiltres());
        cboFamille.addActionListener(e -> appliquerFiltres());
        chkInactifs.addActionListener(e -> appliquerFiltres());

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openDialogSelection();
            }
        });

        // ── Panneau titre ─────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lbl = new JLabel("Catalogue Produits & Familles");
        lbl.setFont(UIConstants.FONT_TITRE);
        header.add(lbl, BorderLayout.WEST);
        header.add(toolbar, BorderLayout.SOUTH);

        // ── Table ─────────────────────────────────────────────────
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        // ── Footer ────────────────────────────────────────────────
        JLabel lblFooter = new JLabel("Affichage 0 produits");
        lblFooter.setFont(UIConstants.FONT_PETIT);
        lblFooter.setForeground(UIConstants.GRIS_TEXTE);
        lblFooter.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(lblFooter, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        appliquerFiltres();
    }

    private void appliquerFiltres() {
        String texte = searchField.getText();
        Famille fam  = (Famille) cboFamille.getSelectedItem();
        boolean inclInactifs = chkInactifs.isSelected();
        String famId = (fam != null && !fam.getId().isEmpty()) ? fam.getId() : null;

        SwingWorker<List<Produit>, Void> w = new SwingWorker<>() {
            @Override protected List<Produit> doInBackground() {
                return produitDAO.search(texte, famId, inclInactifs);
            }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void majTable(List<Produit> list) {
        this.produits = list;
        tableModel.setRowCount(0);
        for (Produit p : list) {
            tableModel.addRow(new Object[]{
                p.getCode(),
                p.getLibelle(),
                p.getFamille() != null ? p.getFamille().getNom() : "—",
                p.getUnite(),
                p.getStatut().name(),
                p.getSeuilAlerte()
            });
        }
        table.autoResizeColumns();
    }

    private void openDialogSelection() {
        int row = table.getSelectedRow();
        if (row < 0 || produits == null || row >= produits.size()) return;
        openDialog(produits.get(row));
    }

    private void openDialog(Produit produit) {
        ProduitDialog dlg = new ProduitDialog((Frame) SwingUtilities.getWindowAncestor(this), produit);
        dlg.setVisible(true);
        if (dlg.isSaved()) refresh();
    }

    // Renderer badge pour la colonne Statut
    private static class BadgeRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            StatusBadge badge = StatusBadge.forStatut(value != null ? value.toString() : "");
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            if (isSelected) badge.setBackground(UIConstants.BLEU_CLAIR);
            return badge;
        }
    }
}
