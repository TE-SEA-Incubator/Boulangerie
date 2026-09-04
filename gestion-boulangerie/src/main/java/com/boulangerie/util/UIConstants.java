package com.boulangerie.util;

import java.awt.*;

/**
 * Palette de couleurs et constantes UI conformes aux maquettes Planche 1-3.
 */
public final class UIConstants {
    private UIConstants() {}

    // ── Couleurs primaires ────────────────────────────────────────
    public static final Color BLEU_PRIMAIRE   = new Color(0x1A73E8);
    public static final Color BLEU_SURVOL     = new Color(0x1558B0);
    public static final Color BLEU_CLAIR      = new Color(0xE8F0FE);

    // ── Couleurs de statut ────────────────────────────────────────
    public static final Color VERT_SUCCES     = new Color(0x0F9D58);
    public static final Color VERT_CLAIR      = new Color(0xE6F4EA);
    public static final Color ORANGE_ALERTE   = new Color(0xF29900);
    public static final Color ORANGE_CLAIR    = new Color(0xFEF7E0);
    public static final Color ROUGE_DANGER    = new Color(0xD93025);
    public static final Color ROUGE_CLAIR     = new Color(0xFCE8E6);

    // ── Neutres ───────────────────────────────────────────────────
    public static final Color GRIS_FOND       = new Color(0xF4F6FA);
    public static final Color GRIS_BORDURE    = new Color(0xDADCE0);
    public static final Color GRIS_TEXTE      = new Color(0x5F6368);
    public static final Color BLANC           = Color.WHITE;
    public static final Color NOIR_TEXTE      = new Color(0x202124);

    // ── Couleurs catégories clients ───────────────────────────────
    public static final Color VIOLET_CARREFOUR = new Color(0x9334E6);
    public static final Color VIOLET_CLAIR     = new Color(0xF3E8FD);

    // ── Badges statut ─────────────────────────────────────────────
    public static final Color BADGE_ACTIF     = VERT_SUCCES;
    public static final Color BADGE_BLOQUE    = ROUGE_DANGER;
    public static final Color BADGE_INACTIF   = new Color(0x9AA0A6);
    public static final Color BADGE_BROUILLON = ORANGE_ALERTE;
    public static final Color BADGE_EN_COURS  = BLEU_PRIMAIRE;
    public static final Color BADGE_COMPLETE  = VERT_SUCCES;

    // ── Polices (Segoe UI partout comme les maquettes) ────────────
    public static final Font FONT_TITRE      = new Font("Segoe UI", Font.BOLD,  20);
    public static final Font FONT_SOUS_TITRE = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_NORMAL     = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_PETIT      = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BOLD       = new Font("Segoe UI", Font.BOLD,  13);
    public static final Font FONT_MONO       = new Font("Courier New", Font.PLAIN, 12);

    // ── Dimensions ────────────────────────────────────────────────
    public static final int  BARRE_NAV_HEIGHT    = 52;
    public static final int  BARRE_STATUS_HEIGHT = 28;
    public static final Insets PADDING_PANEL     = new Insets(16, 20, 16, 20);

    // ── Utilitaire : dimension adaptée à l'écran ─────────────────
    public static Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    /** Padding standard pour les panneaux principaux */
    public static Insets paddingPanel() {
        return new Insets(16, 20, 16, 20);
    }
}
