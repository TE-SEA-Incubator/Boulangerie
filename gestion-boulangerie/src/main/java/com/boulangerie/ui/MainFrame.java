package com.boulangerie.ui;

import com.boulangerie.service.AuthService;
import com.boulangerie.service.SessionService;
import com.boulangerie.ui.panels.*;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fenêtre principale — MAXIMISÉE sur l'écran, responsive.
 * Navigation par CardLayout. Couleurs et polices conformes aux maquettes.
 */
public class MainFrame extends JFrame {

    private final CardLayout    cardLayout    = new CardLayout();
    private final JPanel        contentPanel  = new JPanel(cardLayout);
    private final SessionService session      = SessionService.getInstance();

    private JLabel lblUtilisateur;
    private JLabel lblHeure;
    private JButton activeNavBtn;
    private Timer   heureTimer;

    // Noms des cartes
    public static final String CARD_DASHBOARD    = "DASHBOARD";
    public static final String CARD_PRODUITS     = "PRODUITS";
    public static final String CARD_CLIENTS      = "CLIENTS";
    public static final String CARD_SORTIES      = "SORTIES";
    public static final String CARD_FACTURATION  = "FACTURATION";
    public static final String CARD_CAISSE       = "CAISSE";
    public static final String CARD_RECOUVREMENT = "RECOUVREMENT";
    public static final String CARD_UTILISATEURS = "UTILISATEURS";
    public static final String CARD_RAPPORTS     = "RAPPORTS";
    public static final String CARD_AUDIT        = "AUDIT";
    public static final String CARD_PARAMETRES   = "PARAMETRES";

    public MainFrame() {
        setTitle("Gestion Boulangerie");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // ── Taille : maximisée, responsive ────────────────────────
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screen = tk.getScreenSize();
        setSize(screen.width, screen.height);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1024, 640));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                confirmerFermeture();
            }
        });

        setLayout(new BorderLayout());
        add(buildNavBar(),    BorderLayout.NORTH);
        add(contentPanel,     BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        registerPanels();
        showPanel(CARD_DASHBOARD);

        // Horloge
        heureTimer = new Timer(1000, e -> updateHeure());
        heureTimer.start();

        // Vérif session inactivité
        new Timer(60_000, e -> checkSession()).start();
    }

    // ── Barre de navigation ───────────────────────────────────────
    private JPanel buildNavBar() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(Color.WHITE);
        nav.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GRIS_BORDURE));
        nav.setPreferredSize(new Dimension(0, 52));

        // Logo gauche
        JLabel lblLogo = new JLabel("  🥖  Gestion Boulangerie");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblLogo.setForeground(new Color(0x6B3A2A));
        lblLogo.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 16));
        nav.add(lblLogo, BorderLayout.WEST);

        // Boutons de navigation centraux
        JPanel menuPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        menuPanel.setOpaque(false);

        addNavBtn(menuPanel, "Produits",       CARD_PRODUITS);
        addNavBtn(menuPanel, "Clients",        CARD_CLIENTS);
        addNavBtn(menuPanel, "Sorties/Retours",CARD_SORTIES);
        addNavBtn(menuPanel, "Facturation",    CARD_FACTURATION);
        addNavBtn(menuPanel, "Caisse",         CARD_CAISSE);
        addNavBtn(menuPanel, "Recouvrement",   CARD_RECOUVREMENT);
        addNavBtn(menuPanel, "Utilisateurs",   CARD_UTILISATEURS);
        addNavBtn(menuPanel, "Rapports",       CARD_RAPPORTS);
        addNavBtn(menuPanel, "Audit",          CARD_AUDIT);
        addNavBtn(menuPanel, "⚙ Paramètres",  CARD_PARAMETRES);

        nav.add(menuPanel, BorderLayout.CENTER);

        // Utilisateur + déconnexion droite
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);

        lblUtilisateur = new JLabel();
        lblUtilisateur.setFont(UIConstants.FONT_PETIT);
        lblUtilisateur.setForeground(UIConstants.GRIS_TEXTE);
        if (session.isConnecte()) {
            lblUtilisateur.setText(session.getUtilisateur().getNomComplet()
                + "  |  " + session.getUtilisateur().getRole().getNom());
        }
        rightPanel.add(lblUtilisateur);

        JButton btnDeconn = new JButton("⏻");
        btnDeconn.setToolTipText("Déconnexion");
        btnDeconn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btnDeconn.setForeground(UIConstants.ROUGE_DANGER);
        btnDeconn.setBorderPainted(false);
        btnDeconn.setContentAreaFilled(false);
        btnDeconn.setFocusPainted(false);
        btnDeconn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDeconn.addActionListener(e -> deconnecter());
        rightPanel.add(btnDeconn);

        nav.add(rightPanel, BorderLayout.EAST);
        return nav;
    }

    private void addNavBtn(JPanel panel, String label, String card) {
        JButton btn = new JButton(label);
        btn.setFont(UIConstants.FONT_NORMAL);
        btn.setForeground(UIConstants.GRIS_TEXTE);
        btn.setBackground(Color.WHITE);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 12, 52));

        btn.addActionListener(e -> {
            showPanel(card);
            setActiveBtn(btn);
        });
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn != activeNavBtn) btn.setForeground(UIConstants.BLEU_PRIMAIRE);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                if (btn != activeNavBtn) btn.setForeground(UIConstants.GRIS_TEXTE);
            }
        });
        panel.add(btn);
    }

    private void setActiveBtn(JButton btn) {
        if (activeNavBtn != null) {
            activeNavBtn.setForeground(UIConstants.GRIS_TEXTE);
            activeNavBtn.setContentAreaFilled(false);
        }
        activeNavBtn = btn;
        btn.setForeground(UIConstants.BLEU_PRIMAIRE);
        // Soulignement actif simulé via background
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBackground(UIConstants.BLEU_CLAIR);
    }

    // ── Barre de statut ───────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UIConstants.GRIS_FOND);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.GRIS_BORDURE),
            BorderFactory.createEmptyBorder(3, 14, 3, 14)));
        bar.setPreferredSize(new Dimension(0, 28));

        JLabel lblUserInfo = new JLabel();
        lblUserInfo.setFont(UIConstants.FONT_PETIT);
        lblUserInfo.setForeground(UIConstants.GRIS_TEXTE);
        if (session.isConnecte()) {
            lblUserInfo.setText("Utilisateur : "
                + session.getUtilisateur().getNomComplet()
                + "   |   Rôle : "
                + session.getUtilisateur().getRole().getNom());
        }
        bar.add(lblUserInfo, BorderLayout.WEST);

        lblHeure = new JLabel();
        lblHeure.setFont(UIConstants.FONT_PETIT);
        lblHeure.setForeground(UIConstants.GRIS_TEXTE);
        updateHeure();
        bar.add(lblHeure, BorderLayout.EAST);
        return bar;
    }

    // ── Enregistrement des panneaux ───────────────────────────────
    private void registerPanels() {
        contentPanel.setBackground(UIConstants.GRIS_FOND);
        contentPanel.add(new DashboardPanel(this),    CARD_DASHBOARD);
        contentPanel.add(new ProduitsPanel(this),     CARD_PRODUITS);
        contentPanel.add(new ClientsPanel(this),      CARD_CLIENTS);
        contentPanel.add(new SortiesPanel(this),      CARD_SORTIES);
        contentPanel.add(new FacturationPanel(this),  CARD_FACTURATION);
        contentPanel.add(new CaissePanel(this),       CARD_CAISSE);
        contentPanel.add(new RecouvrementPanel(this), CARD_RECOUVREMENT);
        contentPanel.add(new UtilisateursPanel(this), CARD_UTILISATEURS);
        contentPanel.add(new RapportsPanel(this),     CARD_RAPPORTS);
        contentPanel.add(new AuditPanel(this),        CARD_AUDIT);
        contentPanel.add(new ParametresPanel(this),   CARD_PARAMETRES);
    }

    // ── Navigation ────────────────────────────────────────────────
    public void showPanel(String card) {
        if (!hasAccess(card)) {
            JOptionPane.showMessageDialog(this,
                "Accès refusé pour ce module.", "Accès refusé",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        cardLayout.show(contentPanel, card);
        session.rafraichir();
        // Rafraîchir le panneau visible
        for (Component c : contentPanel.getComponents()) {
            if (c.isVisible() && c instanceof Refreshable r) {
                SwingUtilities.invokeLater(r::refresh);
                break;
            }
        }
    }

    private boolean hasAccess(String card) {
        if (!session.isConnecte()) return false;
        return switch (card) {
            case CARD_UTILISATEURS -> session.hasPermission("USER_WRITE");
            case CARD_AUDIT        -> session.hasPermission("AUDIT_READ");
            case CARD_PARAMETRES   -> session.isAdmin();
            default -> true;
        };
    }

    // ── Session ───────────────────────────────────────────────────
    private void checkSession() {
        if (session.isExpiree()) {
            JOptionPane.showMessageDialog(this,
                "Votre session a expiré. Veuillez vous reconnecter.",
                "Session expirée", JOptionPane.WARNING_MESSAGE);
            deconnecter();
        }
    }

    private void deconnecter() {
        if (heureTimer != null) heureTimer.stop();
        new AuthService().deconnecter();
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    private void confirmerFermeture() {
        int r = JOptionPane.showConfirmDialog(this,
            "Voulez-vous quitter l'application ?",
            "Quitter", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            if (heureTimer != null) heureTimer.stop();
            dispose();
            System.exit(0);
        }
    }

    private void updateHeure() {
        if (lblHeure != null) {
            lblHeure.setText(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(
                    "EEEE d MMMM yyyy   HH:mm:ss", Locale.FRENCH)));
        }
    }

    /** Interface pour les panneaux rafraîchissables. */
    public interface Refreshable { void refresh(); }
}
