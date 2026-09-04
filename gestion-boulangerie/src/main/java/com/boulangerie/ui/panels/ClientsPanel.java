package com.boulangerie.ui.panels;

import com.boulangerie.dao.ClientDAO;
import com.boulangerie.model.*;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.ui.dialogs.ClientDialog;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ClientsPanel extends JPanel implements MainFrame.Refreshable {

    private final MainFrame mainFrame;
    private final ClientDAO clientDAO = new ClientDAO();
    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private final SearchField searchField = new SearchField("Rechercher un client...");
    private final JLabel lblCount = new JLabel("Affichage 0 clients");
    private List<Client> clients;

    public ClientsPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = {"Code", "Nom du client", "Quartier / Ville", "Téléphone",
                         "Catégorie", "Type", "Livreur rattaché", "Solde (FCFA)",
                         "Plafond", "% utilisé", "Délai dépassé", "Statut"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 9 ? Double.class : String.class;
            }
        };
        table = new StyledTable(tableModel);
        table.getColumnModel().getColumn(9).setMaxWidth(95);
        table.getColumnModel().getColumn(10).setMaxWidth(110);
        // Badges : Catégorie(4), Type(5), Delai(10), Statut(11)
        for (int col : new int[]{4, 5, 10, 11}) {
            final int cc = col;
            table.getColumnModel().getColumn(col).setCellRenderer((tbl, val, sel, foc, row, c) -> {
                String s = val == null ? "" : val.toString();
                StatusBadge badge = StatusBadge.forStatut(s);
                if (cc == 10 && "OK".equals(s)) {
                    badge = new StatusBadge("OK", UIConstants.VERT_SUCCES);
                }
                if (sel) {
                    badge.setOpaque(false);
                }
                return badge;
            });
        }
        // Solde (col 7) : coloré
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(RIGHT);
                setFont(UIConstants.FONT_BOLD);
                if (!sel && val != null) {
                    try {
                        double v = Double.parseDouble(val.toString().replace(" ","").replace(",","."));
                        setForeground(v > 0 ? UIConstants.ROUGE_DANGER : UIConstants.VERT_SUCCES);
                        setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF8F9FA));
                    } catch (Exception ignore) {}
                }
                return c;
            }
        });
        // % plafond utilisé (col 9) : barre visuelle couleur
        table.getColumnModel().getColumn(9).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                Object raw = val;
                double pct = 0;
                try { pct = ((Number) val).doubleValue(); } catch (Exception ignore) {}
                String txt = String.format("%.0f %%", Math.min(pct, 999));
                Color bg;
                Color fg;
                if (pct >= 100) { bg = UIConstants.ROUGE_CLAIR;   fg = UIConstants.ROUGE_DANGER; }
                else if (pct >= 80) { bg = UIConstants.ORANGE_CLAIR; fg = UIConstants.ORANGE_ALERTE; }
                else if (pct > 0)   { bg = UIConstants.VERT_CLAIR;   fg = UIConstants.VERT_SUCCES; }
                else                { bg = Color.WHITE;              fg = UIConstants.GRIS_TEXTE; }

                JLabel lbl = new JLabel(txt, SwingConstants.CENTER);
                lbl.setOpaque(true);
                lbl.setFont(UIConstants.FONT_BOLD);
                lbl.setForeground(fg);
                if (sel) {
                    lbl.setBackground(UIConstants.BLEU_CLAIR);
                    lbl.setForeground(UIConstants.NOIR_TEXTE);
                } else {
                    lbl.setBackground(bg);
                }
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                return lbl;
            }
        });
        // Plafond (col 8) : droite + normal
        table.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(RIGHT);
                return c;
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openDialog(getSelected());
            }
        });

        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lbl = new JLabel("Gestion Clients");
        lbl.setFont(UIConstants.FONT_TITRE);
        header.add(lbl, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        RoundedButton btnNouv  = new RoundedButton("+ Nouveau",   RoundedButton.Style.PRIMARY);
        RoundedButton btnImp   = new RoundedButton("Imprimer",    RoundedButton.Style.SECONDARY);
        RoundedButton btnPDF   = new RoundedButton("Export PDF",  RoundedButton.Style.SECONDARY);
        RoundedButton btnBlock = new RoundedButton("Bloquer",     RoundedButton.Style.DANGER);
        RoundedButton btnDeb   = new RoundedButton("Débloquer",   RoundedButton.Style.SUCCESS);
        toolbar.add(btnNouv); toolbar.add(btnImp); toolbar.add(btnPDF);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(btnBlock); toolbar.add(btnDeb);
        toolbar.add(Box.createHorizontalStrut(16));
        toolbar.add(searchField);
        toolbar.add(new JLabel("Filtres"));

        btnNouv.addActionListener(e -> openDialog(null));
        btnBlock.addActionListener(e -> changerStatut(Client.Statut.Bloqué));
        btnDeb.addActionListener(e -> changerStatut(Client.Statut.Actif));
        searchField.addActionListener(e -> appliquerFiltres());
        header.add(toolbar, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        lblCount.setFont(UIConstants.FONT_PETIT);
        lblCount.setForeground(UIConstants.GRIS_TEXTE);
        footer.add(lblCount, BorderLayout.WEST);

        JPanel pageNav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pageNav.setOpaque(false);
        pageNav.add(new JLabel("10 / page"));
        footer.add(pageNav, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() { appliquerFiltres(); }

    private void appliquerFiltres() {
        String texte = searchField.getText();
        SwingWorker<List<Client>, Void> w = new SwingWorker<>() {
            @Override protected List<Client> doInBackground() {
                return clientDAO.search(texte, null, null, false);
            }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void majTable(List<Client> list) {
        this.clients = list;
        tableModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (Client c : list) {
            BigDecimal plafond = c.getPlafondCredit() == null ? BigDecimal.ZERO : c.getPlafondCredit();
            BigDecimal solde   = c.getSoldeActuel() == null ? BigDecimal.ZERO : c.getSoldeActuel();
            double pct = 0;
            if (plafond.compareTo(BigDecimal.ZERO) > 0 && solde.compareTo(BigDecimal.ZERO) > 0) {
                pct = solde.multiply(BigDecimal.valueOf(100))
                            .divide(plafond, 2, java.math.RoundingMode.HALF_UP).doubleValue();
            }

            // Statut dérivé : Actif OK + plafond utilisé >= 80 → badge ORANGE "Plafond proche"
            // Statut dérivé : délai dépassé
            String statutLib;
            if (c.getStatut() == Client.Statut.Bloqué) {
                statutLib = "Bloqué";
            } else if (c.getStatut() == Client.Statut.Inactif) {
                statutLib = "Inactif";
            } else if (pct >= 100) {
                statutLib = "Plafond dépassé";
            } else if (pct >= 80) {
                statutLib = "Plafond proche";
            } else {
                statutLib = "Actif";
            }

            // Délai dépassé : dernière facture impayée > délaiPaiement
            String delaiLib = "OK";
            if (c.getTypeClient() == Client.TypeClient.Nominatif
                && c.getDelaiPaiementJours() != null
                && solde.compareTo(BigDecimal.ZERO) > 0
                && c.getDerniereFactureDate() != null) {
                LocalDate echeance = c.getDerniereFactureDate().plusDays(c.getDelaiPaiementJours());
                if (today.isAfter(echeance)) {
                    delaiLib = "Dépassé";
                }
            }

            tableModel.addRow(new Object[]{
                c.getCode(), c.getNom(),
                (c.getQuartier() != null ? c.getQuartier() : "") + (c.getVille() != null ? " " + c.getVille() : ""),
                c.getTelephone() != null ? c.getTelephone() : "—",
                c.getCategorie() != null ? c.getCategorie().getNom() : "—",
                c.getTypeClient().name(),
                c.getLivreurRattache() != null ? c.getLivreurRattache().getNomComplet() : "—",
                FormatUtil.montant(solde),
                FormatUtil.montant(plafond),
                Math.max(0.0, Math.min(999.0, pct)),
                delaiLib,
                statutLib
            });
        }
        lblCount.setText("Affichage " + list.size() + " client" + (list.size() > 1 ? "s" : ""));
        table.autoResizeColumns();
    }

    private Client getSelected() {
        int row = table.getSelectedRow();
        return (row >= 0 && clients != null && row < clients.size()) ? clients.get(row) : null;
    }

    private void openDialog(Client client) {
        ClientDialog dlg = new ClientDialog((Frame) SwingUtilities.getWindowAncestor(this), client);
        dlg.setVisible(true);
        if (dlg.isSaved()) refresh();
    }

    private void changerStatut(Client.Statut cible) {
        Client c = getSelected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez un client.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "<html>Confirmer le passage de <b>" + c.getNom() + "</b> au statut : <b>" + cible.name() + "</b> ?</html>",
            "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            clientDAO.updateStatut(c.getId(), cible);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
}
