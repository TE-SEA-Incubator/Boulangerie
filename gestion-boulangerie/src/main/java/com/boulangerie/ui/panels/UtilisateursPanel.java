package com.boulangerie.ui.panels;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.dao.UtilisateurDAO;
import com.boulangerie.model.*;
import com.boulangerie.service.AuthService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.*;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class UtilisateursPanel extends JPanel implements MainFrame.Refreshable {

    private final UtilisateurDAO userDAO  = new UtilisateurDAO();
    private final AuditDAO       auditDAO = new AuditDAO();
    private final SessionService session  = SessionService.getInstance();

    private final DefaultTableModel tableModel;
    private final StyledTable table;
    private List<Utilisateur> utilisateurs;

    public UtilisateursPanel(MainFrame mainFrame) {
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = {"Login","Nom complet","Rôle","Téléphone","Email","Dernière connexion","Actif"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new StyledTable(tableModel);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) modifierSelectionne();
            }
        });

        buildUI();
        refresh();
    }

    private void buildUI() {
        JLabel lbl = new JLabel("Gestion Utilisateurs & Rôles");
        lbl.setFont(UIConstants.FONT_TITRE);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setOpaque(false);
        RoundedButton btnNouv  = new RoundedButton("+ Nouvel utilisateur", RoundedButton.Style.PRIMARY);
        RoundedButton btnMdp   = new RoundedButton("Changer mot de passe",  RoundedButton.Style.OUTLINE);
        RoundedButton btnActif = new RoundedButton("Activer / Désactiver",   RoundedButton.Style.SECONDARY);
        toolbar.add(btnNouv); toolbar.add(btnMdp); toolbar.add(btnActif);

        btnNouv.addActionListener(e  -> ouvrirDialog(null));
        btnMdp.addActionListener(e   -> changerMotDePasse());
        btnActif.addActionListener(e -> toggleActif());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(lbl,     BorderLayout.WEST);
        header.add(toolbar, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE));
        scroll.getViewport().setBackground(Color.WHITE);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        SwingWorker<List<Utilisateur>, Void> w = new SwingWorker<>() {
            @Override protected List<Utilisateur> doInBackground() { return userDAO.findAll(); }
            @Override protected void done() {
                try { majTable(get()); } catch (Exception ignore) {}
            }
        };
        w.execute();
    }

    private void majTable(List<Utilisateur> list) {
        this.utilisateurs = list;
        tableModel.setRowCount(0);
        for (Utilisateur u : list) {
            tableModel.addRow(new Object[]{
                u.getLogin(), u.getNomComplet(),
                u.getRole() != null ? u.getRole().getNom() : "—",
                u.getTelephone() != null ? u.getTelephone() : "—",
                u.getEmail() != null ? u.getEmail() : "—",
                FormatUtil.dateHeure(u.getDerniereConnexion()),
                u.isActif() ? "✓ Actif" : "✗ Inactif"
            });
        }
        table.autoResizeColumns();
    }

    private void ouvrirDialog(Utilisateur u) {
        // Dialog de création/modification simplifié
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
            u == null ? "Nouvel utilisateur" : "Modifier " + u.getLogin(), true);
        dlg.setSize(420, 380);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridLayout(6, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JTextField txtLogin  = new JTextField(u != null ? u.getLogin() : "");
        JTextField txtNom    = new JTextField(u != null ? u.getNomComplet() : "");
        JTextField txtTel    = new JTextField(u != null && u.getTelephone() != null ? u.getTelephone() : "");
        JTextField txtEmail  = new JTextField(u != null && u.getEmail() != null ? u.getEmail() : "");
        JPasswordField txtMdp = new JPasswordField();

        List<Role> roles = userDAO.findAllRoles();
        JComboBox<Role> cboRole = new JComboBox<>(roles.toArray(new Role[0]));
        if (u != null && u.getRole() != null) {
            for (int i = 0; i < cboRole.getItemCount(); i++) {
                if (cboRole.getItemAt(i).getId().equals(u.getRole().getId())) { cboRole.setSelectedIndex(i); break; }
            }
        }

        p.add(new JLabel("Login *"));    p.add(txtLogin);
        p.add(new JLabel("Nom complet *")); p.add(txtNom);
        p.add(new JLabel("Téléphone"));  p.add(txtTel);
        p.add(new JLabel("Email"));      p.add(txtEmail);
        p.add(new JLabel("Rôle *"));     p.add(cboRole);
        if (u == null) { p.add(new JLabel("Mot de passe *")); p.add(txtMdp); }

        JButton btnSave = new JButton("Enregistrer");
        btnSave.addActionListener(ev -> {
            if (txtLogin.getText().isBlank() || txtNom.getText().isBlank()) {
                JOptionPane.showMessageDialog(dlg, "Login et Nom obligatoires.");
                return;
            }
            try {
                Utilisateur ut = u != null ? u : new Utilisateur();
                ut.setLogin(txtLogin.getText().trim());
                ut.setNomComplet(txtNom.getText().trim());
                ut.setTelephone(txtTel.getText().trim());
                ut.setEmail(txtEmail.getText().trim());
                ut.setRole((Role) cboRole.getSelectedItem());
                ut.setActif(true);
                if (u == null) {
                    String mdp = new String(txtMdp.getPassword());
                    if (mdp.isBlank()) { JOptionPane.showMessageDialog(dlg, "Mot de passe obligatoire."); return; }
                    ut.setMotDePasse(AuthService.hasher(mdp));
                    userDAO.save(ut);
                    auditDAO.log(new JournalAudit("Utilisateur", null, JournalAudit.CREATE,
                        session.getUserId(), session.getLogin(), "Nouvel utilisateur: " + ut.getLogin()));
                } else {
                    userDAO.update(ut);
                    auditDAO.log(new JournalAudit("Utilisateur", ut.getId(), JournalAudit.UPDATE,
                        session.getUserId(), session.getLogin(), "Modification: " + ut.getLogin()));
                }
                dlg.dispose();
                refresh();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Erreur: " + ex.getMessage());
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(btnSave);
        footer.add(new JButton("Annuler") {{ addActionListener(ev -> dlg.dispose()); }});

        dlg.setLayout(new BorderLayout());
        dlg.add(p, BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void modifierSelectionne() {
        int row = table.getSelectedRow();
        if (row >= 0 && utilisateurs != null && row < utilisateurs.size()) {
            ouvrirDialog(utilisateurs.get(row));
        }
    }

    private void changerMotDePasse() {
        int row = table.getSelectedRow();
        if (row < 0 || utilisateurs == null || row >= utilisateurs.size()) return;
        Utilisateur u = utilisateurs.get(row);
        JPasswordField txtNew = new JPasswordField();
        int r = JOptionPane.showConfirmDialog(this, new Object[]{"Nouveau mot de passe :", txtNew},
            "Changer mot de passe", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            String mdp = new String(txtNew.getPassword());
            if (!mdp.isBlank()) {
                userDAO.updatePassword(u.getId(), AuthService.hasher(mdp));
                auditDAO.log(new JournalAudit("Utilisateur", u.getId(), JournalAudit.UPDATE,
                    session.getUserId(), session.getLogin(), "Mot de passe modifié pour: " + u.getLogin()));
                JOptionPane.showMessageDialog(this, "Mot de passe modifié.");
            }
        }
    }

    private void toggleActif() {
        int row = table.getSelectedRow();
        if (row < 0 || utilisateurs == null || row >= utilisateurs.size()) return;
        Utilisateur u = utilisateurs.get(row);
        u.setActif(!u.isActif());
        userDAO.update(u);
        auditDAO.log(new JournalAudit("Utilisateur", u.getId(), JournalAudit.UPDATE,
            session.getUserId(), session.getLogin(),
            (u.isActif() ? "Activation" : "Désactivation") + " utilisateur: " + u.getLogin()));
        refresh();
    }
}
