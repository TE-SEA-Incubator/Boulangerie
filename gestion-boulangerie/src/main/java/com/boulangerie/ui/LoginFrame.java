package com.boulangerie.ui;

import com.boulangerie.service.AuthService;
import com.boulangerie.ui.components.RoundedButton;
import com.boulangerie.util.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Fenêtre de connexion — centrée, taille fixe 460×520, non redimensionnable.
 * Reproduit fidèlement l'écran n°1 de la Planche 1.
 */
public class LoginFrame extends JFrame {

    private final JTextField     txtLogin;
    private final JPasswordField txtPassword;
    private final JLabel         lblError;
    private final AuthService    authService = new AuthService();

    public LoginFrame() {
        setTitle("Gestion Boulangerie — Connexion");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // Taille fixe centrée sur l'écran
        setSize(460, 530);
        setLocationRelativeTo(null);

        // Fond général
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(UIConstants.GRIS_FOND);
        setContentPane(root);

        // Carte centrale blanche
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            new EmptyBorder(32, 36, 32, 36)
        ));
        card.setMaximumSize(new Dimension(380, 480));

        // ── Logo ────────────────────────────────────────────────
        JLabel lblIcon = new JLabel("🥖", SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        lblIcon.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblApp = new JLabel("BOULANGERIE", SwingConstants.CENTER);
        lblApp.setFont(new Font("Segoe UI", Font.BOLD, 21));
        lblApp.setForeground(new Color(0x6B3A2A));
        lblApp.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblSlogan = new JLabel("Qualité & Tradition", SwingConstants.CENTER);
        lblSlogan.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblSlogan.setForeground(UIConstants.GRIS_TEXTE);
        lblSlogan.setAlignmentX(CENTER_ALIGNMENT);

        // ── Séparateur ───────────────────────────────────────────
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(UIConstants.GRIS_BORDURE);

        // ── Champ identifiant ────────────────────────────────────
        JLabel lblLoginLabel = new JLabel("Identifiant");
        lblLoginLabel.setFont(UIConstants.FONT_BOLD);
        lblLoginLabel.setAlignmentX(LEFT_ALIGNMENT);

        txtLogin = new JTextField();
        txtLogin.setFont(UIConstants.FONT_NORMAL);
        txtLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        txtLogin.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        txtLogin.setAlignmentX(LEFT_ALIGNMENT);

        // ── Champ mot de passe ───────────────────────────────────
        JLabel lblPassLabel = new JLabel("Mot de passe");
        lblPassLabel.setFont(UIConstants.FONT_BOLD);
        lblPassLabel.setAlignmentX(LEFT_ALIGNMENT);

        txtPassword = new JPasswordField();
        txtPassword.setFont(UIConstants.FONT_NORMAL);
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        txtPassword.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        txtPassword.setAlignmentX(LEFT_ALIGNMENT);

        // ── Message d'erreur ─────────────────────────────────────
        lblError = new JLabel(" ");
        lblError.setFont(UIConstants.FONT_PETIT);
        lblError.setForeground(UIConstants.ROUGE_DANGER);
        lblError.setAlignmentX(CENTER_ALIGNMENT);

        // ── Bouton connexion ─────────────────────────────────────
        RoundedButton btnConnecter = new RoundedButton("Connexion", RoundedButton.Style.PRIMARY);
        btnConnecter.setAlignmentX(CENTER_ALIGNMENT);
        btnConnecter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnConnecter.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConnecter.addActionListener(e -> tentativeConnexion());

        // ── Info déconnexion auto ────────────────────────────────
        JLabel lblInfo = new JLabel(
            "<html><center>⚠ Déconnexion automatique après inactivité</center></html>",
            SwingConstants.CENTER);
        lblInfo.setFont(UIConstants.FONT_PETIT);
        lblInfo.setForeground(UIConstants.GRIS_TEXTE);
        lblInfo.setAlignmentX(CENTER_ALIGNMENT);

        // ── Assemblage carte ─────────────────────────────────────
        card.add(lblIcon);
        card.add(Box.createVerticalStrut(6));
        card.add(lblApp);
        card.add(Box.createVerticalStrut(2));
        card.add(lblSlogan);
        card.add(Box.createVerticalStrut(16));
        card.add(sep);
        card.add(Box.createVerticalStrut(20));
        card.add(lblLoginLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(txtLogin);
        card.add(Box.createVerticalStrut(14));
        card.add(lblPassLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(txtPassword);
        card.add(Box.createVerticalStrut(12));
        card.add(lblError);
        card.add(Box.createVerticalStrut(8));
        card.add(btnConnecter);
        card.add(Box.createVerticalStrut(16));
        card.add(lblInfo);

        root.add(card);

        // Touche Entrée
        txtPassword.addActionListener(e -> tentativeConnexion());
        txtLogin.addActionListener(e -> txtPassword.requestFocus());
    }

    private void tentativeConnexion() {
        String login = txtLogin.getText().trim();
        String mdp   = new String(txtPassword.getPassword());

        if (login.isEmpty() || mdp.isEmpty()) {
            lblError.setText("Veuillez saisir votre identifiant et mot de passe.");
            return;
        }
        lblError.setText(" ");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String errorMsg;
            @Override protected Void doInBackground() {
                try { authService.connecter(login, mdp); }
                catch (Exception ex) { errorMsg = ex.getMessage(); }
                return null;
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                if (errorMsg != null) {
                    lblError.setText(errorMsg);
                    txtPassword.setText("");
                    txtPassword.requestFocus();
                } else {
                    ouvrirApplication();
                }
            }
        };
        worker.execute();
    }

    private void ouvrirApplication() {
        dispose();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
