package com.boulangerie.ui.components;

import com.boulangerie.util.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Bouton avec coins arrondis et effet de survol. */
public class RoundedButton extends JButton {

    public enum Style { PRIMARY, SUCCESS, DANGER, SECONDARY, OUTLINE }

    private final Style style;
    private Color bgColor;
    private Color hoverColor;
    private Color fgColor;
    private final int arc = 8;

    public RoundedButton(String text, Style style) {
        super(text);
        this.style = style;
        applyStyle(style);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFont(UIConstants.FONT_BOLD);
        setForeground(fgColor);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { repaint(); }
            @Override public void mouseExited(MouseEvent e)  { repaint(); }
        });
    }

    public RoundedButton(String text) { this(text, Style.PRIMARY); }

    public Style getButtonStyle() { return style; }

    private void applyStyle(Style style) {
        switch (style) {
            case PRIMARY   -> { bgColor = UIConstants.BLEU_PRIMAIRE; hoverColor = UIConstants.BLEU_SURVOL; fgColor = Color.WHITE; }
            case SUCCESS   -> { bgColor = UIConstants.VERT_SUCCES;   hoverColor = new Color(0x0A7A47);    fgColor = Color.WHITE; }
            case DANGER    -> { bgColor = UIConstants.ROUGE_DANGER;  hoverColor = new Color(0xB02319);    fgColor = Color.WHITE; }
            case SECONDARY -> { bgColor = UIConstants.GRIS_TEXTE;    hoverColor = new Color(0x4A4F54);    fgColor = Color.WHITE; }
            case OUTLINE   -> { bgColor = Color.WHITE; hoverColor = UIConstants.GRIS_FOND; fgColor = UIConstants.BLEU_PRIMAIRE; }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ButtonModel m = getModel();
        boolean pressed = m.isPressed() && m.isArmed();
        boolean hovered = m.isRollover() && !pressed;
        boolean disabled = !isEnabled();

        if (disabled) {
            g2.setColor(UIConstants.GRIS_BORDURE);
        } else if (pressed) {
            g2.setColor(style == Style.OUTLINE ? UIConstants.BLEU_CLAIR : darker(hovered ? hoverColor : bgColor));
        } else {
            g2.setColor(hovered ? hoverColor : bgColor);
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

        if (style == Style.OUTLINE) {
            g2.setColor(disabled ? UIConstants.GRIS_BORDURE : UIConstants.BLEU_PRIMAIRE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
        }
        g2.dispose();

        Graphics gText = g.create();
        gText.setColor(disabled ? UIConstants.GRIS_TEXTE : fgColor);
        super.paintComponent(gText);
        gText.dispose();
    }

    private static Color darker(Color c) {
        if (c == null) return Color.GRAY;
        return new Color(
            Math.max((int)(c.getRed()   * 0.85), 0),
            Math.max((int)(c.getGreen() * 0.85), 0),
            Math.max((int)(c.getBlue()  * 0.85), 0)
        );
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 24, Math.max(d.height + 8, 36));
    }
}
