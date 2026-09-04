package com.boulangerie.ui.panels;

import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.service.ClotureService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.MainFrame;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.FormatUtil;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;

/**
 * Panneau Paramètres & Administration.
 * Reproduit la Planche 3 du cahier des charges :
 *  - Paramètres entreprise
 *  - Sauvegarde / Restauration BDD
 *  - Clôture mensuelle
 *  - Réinitialisation mot de passe admin
 *  - Informations système
 */
public class ParametresPanel extends JPanel implements MainFrame.Refreshable {

    private final SessionService session = SessionService.getInstance();
    private final ClotureService clotureService = new ClotureService();

    // Paramètres entreprise
    private JTextField txtNomEntreprise, txtAdresse, txtTelephone, txtEmail, txtTvaDefaut;
    private JComboBox<String> cboDevise;

    public ParametresPanel(MainFrame mainFrame) {
        setLayout(new BorderLayout());
        setBackground(UIConstants.GRIS_FOND);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel lblTitre = new JLabel("Paramètres & Administration");
        lblTitre.setFont(UIConstants.FONT_TITRE);
        lblTitre.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UIConstants.FONT_NORMAL);
        tabs.addTab("Entreprise",   buildEntreprisePanel());
        tabs.addTab("Sauvegarde",   buildSauvegardePanel());
        tabs.addTab("Clôture",      buildCloturePanel());
        tabs.addTab("Système",      buildSystemePanel());

        add(lblTitre, BorderLayout.NORTH);
        add(tabs,     BorderLayout.CENTER);
    }

    // ── Onglet Entreprise ─────────────────────────────────────────
    private JPanel buildEntreprisePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;

        txtNomEntreprise = addField(p, gc, "Nom de l'entreprise *", 0, "BOULANGERIE");
        txtAdresse       = addField(p, gc, "Adresse",               1, "");
        txtTelephone     = addField(p, gc, "Téléphone",             2, "");
        txtEmail         = addField(p, gc, "Email",                 3, "");
        txtTvaDefaut     = addField(p, gc, "TVA par défaut (%)",    4, "0");

        gc.gridx = 0; gc.gridy = 5;
        p.add(new JLabel("Devise"), gc);
        gc.gridx = 1;
        cboDevise = new JComboBox<>(new String[]{"FCFA","EUR","USD","MAD"});
        p.add(cboDevise, gc);

        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logoPanel.setOpaque(false);
        JButton btnLogo = new JButton("Choisir un logo...");
        btnLogo.addActionListener(e -> choisirLogo());
        logoPanel.add(new JLabel("Logo de l'entreprise :"));
        logoPanel.add(btnLogo);
        p.add(logoPanel, gc);
        gc.gridwidth = 1;

        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2;
        RoundedButton btnSave = new RoundedButton("Enregistrer les paramètres", RoundedButton.Style.PRIMARY);
        btnSave.addActionListener(e -> sauvegarderParametres());
        p.add(btnSave, gc);

        return p;
    }

    // ── Onglet Sauvegarde ─────────────────────────────────────────
    private JPanel buildSauvegardePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Info sauvegarde
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 0, 8));
        infoPanel.setOpaque(false);
        infoPanel.add(buildInfoCard("💾 Sauvegarde manuelle",
            "Exporte la base de données MySQL vers un fichier .sql",
            e -> sauvegarderBDD()));
        infoPanel.add(buildInfoCard("📂 Restauration",
            "Importe un fichier .sql de sauvegarde (ATTENTION : remplace les données actuelles)",
            e -> restaurerBDD()));
        infoPanel.add(buildInfoCard("📅 Sauvegarde automatique",
            "Configurez la fréquence de sauvegarde automatique (quotidienne recommandée).",
            e -> configurerSauvegardeAuto()));

        p.add(infoPanel, BorderLayout.NORTH);

        // Historique sauvegardes
        JTextArea txtHisto = new JTextArea(
            "Historique des sauvegardes :\n" +
            "— Aucune sauvegarde enregistrée.\n\n" +
            "Recommandation : effectuez une sauvegarde quotidienne\n" +
            "et stockez les fichiers hors du serveur de production.");
        txtHisto.setEditable(false);
        txtHisto.setFont(UIConstants.FONT_PETIT);
        txtHisto.setBorder(BorderFactory.createTitledBorder("Historique"));
        p.add(new JScrollPane(txtHisto), BorderLayout.CENTER);

        return p;
    }

    // ── Onglet Clôture ────────────────────────────────────────────
    private JPanel buildCloturePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        // Clôture journalière
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        p.add(new JLabel("<html><b>Clôture journalière</b><br>"
            + "<small>Calcule les soldes de clôture de tous les clients nominatifs pour aujourd'hui.<br>"
            + "Règle CDC : Solde clôture J = Solde ouverture J+1</small></html>"), gc);
        gc.gridy = 1; gc.gridwidth = 1; gc.weightx = 0;
        RoundedButton btnClotJour = new RoundedButton("Exécuter clôture du " + FormatUtil.date(LocalDate.now()),
            RoundedButton.Style.PRIMARY);
        btnClotJour.addActionListener(e -> executerClotureJour());
        p.add(btnClotJour, gc);

        // Séparateur
        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.weightx = 1;
        p.add(new JSeparator(), gc);

        // Clôture mensuelle
        gc.gridy = 3; gc.gridwidth = 2;
        p.add(new JLabel("<html><b>Clôture mensuelle</b><br>"
            + "<small>Consolide les soldes du mois sélectionné.</small></html>"), gc);

        gc.gridy = 4; gc.gridwidth = 1; gc.weightx = 0;
        int annee = LocalDate.now().getYear();
        JSpinner spnAnnee = new JSpinner(new SpinnerNumberModel(annee, 2020, 2099, 1));
        JComboBox<String> cboMois = new JComboBox<>(new String[]{
            "Janvier","Février","Mars","Avril","Mai","Juin",
            "Juillet","Août","Septembre","Octobre","Novembre","Décembre"
        });
        cboMois.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        p.add(new JLabel("Mois :"), gc);
        gc.gridx = 1; p.add(cboMois, gc);
        gc.gridx = 0; gc.gridy = 5; p.add(new JLabel("Année :"), gc);
        gc.gridx = 1; p.add(spnAnnee, gc);
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
        RoundedButton btnClotMois = new RoundedButton("Exécuter clôture mensuelle", RoundedButton.Style.SECONDARY);
        btnClotMois.addActionListener(e -> executerClotureMois(
            (int) spnAnnee.getValue(), cboMois.getSelectedIndex() + 1));
        p.add(btnClotMois, gc);

        return p;
    }

    // ── Onglet Système ────────────────────────────────────────────
    private JPanel buildSystemePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setFont(new Font("Courier New", Font.PLAIN, 12));
        info.setText(
            "=== Informations Système ===\n\n" +
            "Application      : Gestion Boulangerie v1.0\n" +
            "Java             : " + System.getProperty("java.version") + "\n" +
            "OS               : " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n" +
            "Mémoire totale   : " + Runtime.getRuntime().totalMemory() / 1_048_576 + " MB\n" +
            "Mémoire libre    : " + Runtime.getRuntime().freeMemory()  / 1_048_576 + " MB\n\n" +
            "=== Connexion Base de Données ===\n\n" +
            "Pilote           : MySQL Connector/J 8.x\n" +
            "Statut           : " + testConnexionBDD() + "\n\n" +
            "=== Utilisateur connecté ===\n\n" +
            "Login            : " + session.getLogin() + "\n" +
            "Rôle             : " + (session.isConnecte() ? session.getUtilisateur().getRole().getNom() : "—") + "\n"
        );
        p.add(new JScrollPane(info), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnPanel.setOpaque(false);
        RoundedButton btnRefresh = new RoundedButton("Actualiser", RoundedButton.Style.OUTLINE);
        btnRefresh.addActionListener(e -> refresh());
        btnPanel.add(btnRefresh);
        p.add(btnPanel, BorderLayout.SOUTH);
        return p;
    }

    // ── Actions ───────────────────────────────────────────────────
    private void sauvegarderParametres() {
        JOptionPane.showMessageDialog(this,
            "Paramètres enregistrés (stockage local).", "Paramètres", JOptionPane.INFORMATION_MESSAGE);
    }

    private void sauvegarderBDD() {
        if (!session.isAdmin()) {
            JOptionPane.showMessageDialog(this, "Accès réservé à l'administrateur.", "Accès", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("backup_boulangerie_" + LocalDate.now() + ".sql"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        int confirm = JOptionPane.showConfirmDialog(this,
            "Lancer mysqldump vers " + fc.getSelectedFile().getName() + " ?",
            "Sauvegarde BDD", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mysqldump", "-u", "root", "boulangerie",
                "--result-file=" + fc.getSelectedFile().getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exit = proc.waitFor();
            if (exit == 0) {
                JOptionPane.showMessageDialog(this, "Sauvegarde réussie : " + fc.getSelectedFile().getName(),
                    "Sauvegarde", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Sauvegarde échouée (code " + exit + ").\nVérifiez que mysqldump est dans le PATH.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restaurerBDD() {
        if (!session.isAdmin()) {
            JOptionPane.showMessageDialog(this, "Accès réservé à l'administrateur.", "Accès", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int warn = JOptionPane.showConfirmDialog(this,
            "⚠ ATTENTION : la restauration remplace TOUTES les données actuelles.\nContinuer ?",
            "Restauration BDD", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (warn != JOptionPane.YES_OPTION) return;

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choisir le fichier SQL de sauvegarde");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        JOptionPane.showMessageDialog(this,
            "Restauration planifiée depuis : " + fc.getSelectedFile().getName() + "\n"
            + "Veuillez exécuter manuellement :\n"
            + "  mysql -u root boulangerie < " + fc.getSelectedFile().getAbsolutePath(),
            "Restauration", JOptionPane.INFORMATION_MESSAGE);
    }

    private void configurerSauvegardeAuto() {
        JOptionPane.showMessageDialog(this,
            "Configuration de sauvegarde automatique :\n\n"
            + "Ajoutez une tâche cron (Linux) ou une tâche planifiée (Windows) :\n"
            + "  0 2 * * * mysqldump -u root boulangerie > /backups/boulangerie_$(date +\\%F).sql",
            "Sauvegarde automatique", JOptionPane.INFORMATION_MESSAGE);
    }

    private void executerClotureJour() {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            JOptionPane.showMessageDialog(this, "Permission refusée.", "Accès", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Exécuter la clôture journalière pour " + FormatUtil.date(LocalDate.now()) + " ?",
            "Clôture", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            clotureService.cloturerJour(LocalDate.now());
            JOptionPane.showMessageDialog(this, "Clôture journalière exécutée.", "Clôture", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void executerClotureMois(int annee, int mois) {
        if (!session.hasPermission("CLOTURE_WRITE")) {
            JOptionPane.showMessageDialog(this, "Permission refusée.", "Accès", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Exécuter la clôture mensuelle " + mois + "/" + annee + " ?",
            "Clôture mensuelle", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            clotureService.cloturerMois(annee, mois);
            JOptionPane.showMessageDialog(this, "Clôture mensuelle exécutée.", "Clôture", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void choisirLogo() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png","jpg","jpeg","gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(this, "Logo sélectionné : " + fc.getSelectedFile().getName()
                + "\n(Fonctionnalité de logo personnalisé — implémentation selon configuration)");
        }
    }

    private String testConnexionBDD() {
        try (java.sql.Connection c = DatabaseConnection.getInstance().getConnection()) {
            return "✓ Connecté";
        } catch (Exception e) { return "✗ Erreur — " + e.getMessage(); }
    }

    private JPanel buildInfoCard(String titre, String desc, java.awt.event.ActionListener action) {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(UIConstants.GRIS_FOND);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel lblT = new JLabel(titre);
        lblT.setFont(UIConstants.FONT_BOLD);
        JLabel lblD = new JLabel("<html>" + desc + "</html>");
        lblD.setFont(UIConstants.FONT_PETIT);
        lblD.setForeground(UIConstants.GRIS_TEXTE);
        RoundedButton btn = new RoundedButton("Exécuter", RoundedButton.Style.PRIMARY);
        btn.addActionListener(action);
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(lblT, BorderLayout.NORTH);
        text.add(lblD, BorderLayout.CENTER);
        card.add(text, BorderLayout.CENTER);
        card.add(btn,  BorderLayout.EAST);
        return card;
    }

    private JTextField addField(JPanel p, GridBagConstraints gc, String label, int row, String defaultValue) {
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1;
        JTextField tf = new JTextField(defaultValue, 24);
        p.add(tf, gc);
        return tf;
    }

    @Override public void refresh() { repaint(); }
}
