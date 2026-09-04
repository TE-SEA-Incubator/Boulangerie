package com.boulangerie.ui.panels;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.model.JournalAudit;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class AuditPanel extends JPanel implements MainFrame.Refreshable {

    private final AuditDAO auditDAO = new AuditDAO();
    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private JComboBox<String> cboEntite, cboAction;
    private JTextField txtDu, txtAu;
    private JLabel lblTotal;
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 50;

    public AuditPanel(MainFrame mainFrame) {
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = {"Date/Heure","Entité","ID Entité","Action","Utilisateur","Détails"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        buildUI();
    }

    private void buildUI() {
        JLabel lbl = new JLabel("Journal d'Audit");
        lbl.setFont(UIConstants.FONT_TITRE);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);

        cboEntite = new JComboBox<>(new String[]{"Toutes","Utilisateur","Produit","Client","Facture","Versement","Cloture","FicheJournaliere"});
        cboAction = new JComboBox<>(new String[]{"Toutes","CREATE","UPDATE","DELETE","LOGIN","LOGOUT","BLOCK","UNBLOCK","AVOIR","ECART","CLOTURE"});
        txtDu = new JTextField(FormatUtil.date(LocalDate.now().minusDays(7)), 10);
        txtAu = new JTextField(FormatUtil.date(LocalDate.now()), 10);

        RoundedButton btnSearch = new RoundedButton("Rechercher", RoundedButton.Style.PRIMARY);
        RoundedButton btnExport = new RoundedButton("Export CSV",  RoundedButton.Style.OUTLINE);

        toolbar.add(new JLabel("Entité :"));  toolbar.add(cboEntite);
        toolbar.add(new JLabel("Action :"));  toolbar.add(cboAction);
        toolbar.add(new JLabel("Du :"));      toolbar.add(txtDu);
        toolbar.add(new JLabel("Au :"));      toolbar.add(txtAu);
        toolbar.add(btnSearch);
        toolbar.add(btnExport);

        btnSearch.addActionListener(e -> { currentOffset = 0; refresh(); });
        btnExport.addActionListener(e -> exporterCSV());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(lbl, BorderLayout.WEST);
        header.add(toolbar, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        // Pagination
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        lblTotal = new JLabel("0 entrées");
        lblTotal.setFont(UIConstants.FONT_PETIT);
        lblTotal.setForeground(UIConstants.GRIS_TEXTE);
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        navPanel.setOpaque(false);
        JButton btnPrev = new JButton("◀ Précédent");
        JButton btnNext = new JButton("Suivant ▶");
        btnPrev.addActionListener(e -> { if (currentOffset >= PAGE_SIZE) { currentOffset -= PAGE_SIZE; refresh(); } });
        btnNext.addActionListener(e -> { currentOffset += PAGE_SIZE; refresh(); });
        navPanel.add(btnPrev); navPanel.add(btnNext);
        footer.add(lblTotal, BorderLayout.WEST);
        footer.add(navPanel, BorderLayout.EAST);

        // Note immuabilité
        JLabel lblNote = new JLabel("⚠ Le journal d'audit est en lecture seule — aucune modification possible.");
        lblNote.setFont(UIConstants.FONT_PETIT);
        lblNote.setForeground(UIConstants.ORANGE_ALERTE);
        lblNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(footer, BorderLayout.NORTH);
        southPanel.add(lblNote, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String entite  = "Toutes".equals(cboEntite.getSelectedItem()) ? null : (String) cboEntite.getSelectedItem();
        String action  = "Toutes".equals(cboAction.getSelectedItem()) ? null : (String) cboAction.getSelectedItem();
        LocalDate du   = FormatUtil.parseDate(txtDu.getText());
        LocalDate au   = FormatUtil.parseDate(txtAu.getText());

        SwingWorker<List<JournalAudit>, Void> w = new SwingWorker<>() {
            @Override protected List<JournalAudit> doInBackground() {
                return auditDAO.search(entite, action, null, du, au, PAGE_SIZE, currentOffset);
            }
            @Override protected void done() {
                try {
                    List<JournalAudit> list = get();
                    tableModel.setRowCount(0);
                    for (JournalAudit a : list) {
                        tableModel.addRow(new Object[]{
                            FormatUtil.dateHeure(a.getDateAction()),
                            a.getEntite(),
                            a.getEntiteId() != null ? a.getEntiteId().substring(0, Math.min(8, a.getEntiteId().length())) + "..." : "—",
                            a.getAction(),
                            a.getLoginUtilisateur() != null ? a.getLoginUtilisateur() : "—",
                            a.getDetails() != null && a.getDetails().length() > 80
                                ? a.getDetails().substring(0, 80) + "…" : a.getDetails()
                        });
                    }
                    int total = auditDAO.count(entite, action, null, du, au);
                    lblTotal.setText("Page " + (currentOffset / PAGE_SIZE + 1) + " — " + total + " entrées au total");
                    table.autoResizeColumns();
                } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void exporterCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("audit_" + LocalDate.now() + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (java.io.PrintWriter pw = new java.io.PrintWriter(fc.getSelectedFile(), "UTF-8")) {
            pw.println("Date/Heure;Entité;ID Entité;Action;Utilisateur;Détails");
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < tableModel.getColumnCount(); c++) {
                    Object v = tableModel.getValueAt(r, c);
                    sb.append(v != null ? v.toString().replace(";", ",") : "");
                    if (c < tableModel.getColumnCount() - 1) sb.append(";");
                }
                pw.println(sb);
            }
            JOptionPane.showMessageDialog(this, "CSV exporté : " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur export: " + ex.getMessage());
        }
    }
}
