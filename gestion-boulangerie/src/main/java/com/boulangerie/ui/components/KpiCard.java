package com.boulangerie.ui.components;

import com.boulangerie.util.UIConstants;
import javax.swing.*;
import java.awt.*;

/**
 * Carte KPI pour le dashboard (titre, valeur, variation, icône colorée).
 * Reproduit fidèlement les cartes de la Planche 2.
 */
public class KpiCard extends JPanel {
    private final JLabel lblValeur;
    private final JLabel lblVariation;
    private final JLabel lblTitre;

    public KpiCard(String titre, String valeur, String variation, Color accentColor) {
        setLayout(new BorderLayout(0, 4));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));

        // Ligne titre + icône colorée
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        lblTitre = new JLabel(titre);
        lblTitre.setFont(UIConstants.FONT_PETIT);
        lblTitre.setForeground(UIConstants.GRIS_TEXTE);
        topPanel.add(lblTitre, BorderLayout.WEST);

        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentColor);
                g2.fillOval(0, 0, 10, 10);
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(10, 10));
        topPanel.add(dot, BorderLayout.EAST);

        // Valeur principale
        lblValeur = new JLabel(valeur);
        lblValeur.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblValeur.setForeground(UIConstants.NOIR_TEXTE);

        // Variation
        lblVariation = new JLabel(variation);
        lblVariation.setFont(UIConstants.FONT_PETIT);
        boolean positif = variation != null && variation.startsWith("+");
        lblVariation.setForeground(positif ? UIConstants.VERT_SUCCES : UIConstants.ROUGE_DANGER);

        add(topPanel,    BorderLayout.NORTH);
        add(lblValeur,   BorderLayout.CENTER);
        add(lblVariation, BorderLayout.SOUTH);
    }

    public void setValeur(String valeur) { lblValeur.setText(valeur); }
    public void setVariation(String v) {
        lblVariation.setText(v);
        boolean pos = v != null && v.startsWith("+");
        lblVariation.setForeground(pos ? UIConstants.VERT_SUCCES : UIConstants.ROUGE_DANGER);
    }
}
