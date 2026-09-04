package com.boulangerie.ui.components;

import com.boulangerie.util.UIConstants;
import javax.swing.*;
import java.awt.*;

/** Badge coloré pour afficher les statuts dans les tableaux. */
public class StatusBadge extends JLabel {

    public StatusBadge(String text, Color bgColor) {
        super(text, SwingConstants.CENTER);
        setOpaque(false);
        setFont(UIConstants.FONT_PETIT);
        setForeground(Color.WHITE);
        putClientProperty("bgColor", bgColor);
    }

    public static StatusBadge forStatut(String statut) {
        Color bg = switch (statut == null ? "" : statut) {
            case "Actif"       -> UIConstants.BADGE_ACTIF;
            case "Bloqué"      -> UIConstants.BADGE_BLOQUE;
            case "Inactif"     -> UIConstants.BADGE_INACTIF;
            case "Brouillon"   -> UIConstants.BADGE_BROUILLON;
            case "EnCours", "En cours" -> UIConstants.BADGE_EN_COURS;
            case "Complétée"   -> UIConstants.BADGE_COMPLETE;
            case "Payée"       -> UIConstants.VERT_SUCCES;
            case "Partielle"   -> UIConstants.ORANGE_ALERTE;
            case "EnAttente", "En attente" -> UIConstants.GRIS_TEXTE;
            case "Nominatif"   -> UIConstants.BLEU_PRIMAIRE;
            case "Anonyme"     -> UIConstants.GRIS_TEXTE;
            case "Carrefour"   -> UIConstants.VIOLET_CARREFOUR;
            case "Interne"     -> UIConstants.ORANGE_ALERTE;
            case "Externe"     -> UIConstants.VERT_SUCCES;
            default            -> UIConstants.GRIS_TEXTE;
        };
        return new StatusBadge(statut, bg);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = (Color) getClientProperty("bgColor");
        if (bg == null) bg = UIConstants.GRIS_TEXTE;
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 14, 20);
    }
}
