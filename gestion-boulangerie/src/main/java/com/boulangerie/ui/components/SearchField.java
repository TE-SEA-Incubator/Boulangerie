package com.boulangerie.ui.components;

import com.boulangerie.util.UIConstants;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/** Champ de recherche avec placeholder et icône loupe. */
public class SearchField extends JTextField {
    private final String placeholder;
    private boolean showingPlaceholder = true;

    public SearchField(String placeholder) {
        this.placeholder = placeholder;
        setFont(UIConstants.FONT_NORMAL);
        setForeground(UIConstants.GRIS_TEXTE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.GRIS_BORDURE, 1, true),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        setPreferredSize(new Dimension(220, 32));
        addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) {
                if (showingPlaceholder) { setText(""); setForeground(UIConstants.NOIR_TEXTE); showingPlaceholder = false; }
            }
            @Override public void focusLost(FocusEvent e) {
                if (getText().isEmpty()) { setForeground(UIConstants.GRIS_TEXTE); showingPlaceholder = true; repaint(); }
            }
        });
    }

    @Override public String getText() {
        String t = super.getText();
        return showingPlaceholder ? "" : t;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (showingPlaceholder) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(UIConstants.GRIS_TEXTE);
            g2.setFont(getFont().deriveFont(Font.ITALIC));
            g2.drawString(placeholder, 10, getHeight() / 2 + 5);
        }
    }
}
