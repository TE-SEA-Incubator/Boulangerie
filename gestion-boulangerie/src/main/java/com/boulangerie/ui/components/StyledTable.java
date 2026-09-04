package com.boulangerie.ui.components;

import com.boulangerie.util.UIConstants;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

/** JTable avec style moderne (alternance de lignes, en-tête coloré). */
public class StyledTable extends JTable {

    public StyledTable(TableModel model) {
        super(model);
        setup();
    }

    public StyledTable() {
        setup();
    }

    private void setup() {
        setFont(UIConstants.FONT_NORMAL);
        setRowHeight(32);
        setIntercellSpacing(new Dimension(0, 0));
        setShowVerticalLines(false);
        setShowHorizontalLines(true);
        setGridColor(UIConstants.GRIS_BORDURE);
        setSelectionBackground(UIConstants.BLEU_CLAIR);
        setSelectionForeground(UIConstants.NOIR_TEXTE);
        setFillsViewportHeight(true);

        // En-tête
        JTableHeader header = getTableHeader();
        header.setFont(UIConstants.FONT_BOLD);
        header.setBackground(UIConstants.GRIS_FOND);
        header.setForeground(UIConstants.GRIS_TEXTE);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, UIConstants.GRIS_BORDURE));
        header.setReorderingAllowed(false);

        // Renderer alternance lignes
        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF8F9FA));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        });
    }

    /** Ajuste automatiquement la largeur des colonnes au contenu. */
    public void autoResizeColumns() {
        for (int col = 0; col < getColumnCount(); col++) {
            int maxWidth = 0;
            TableColumnModel tcm = getColumnModel();
            TableCellRenderer headerRenderer = getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                    this, tcm.getColumn(col).getHeaderValue(), false, false, 0, col);
            maxWidth = Math.max(maxWidth, headerComp.getPreferredSize().width);
            for (int row = 0; row < getRowCount(); row++) {
                TableCellRenderer r = getCellRenderer(row, col);
                Component c = prepareRenderer(r, row, col);
                maxWidth = Math.max(maxWidth, c.getPreferredSize().width + 16);
            }
            tcm.getColumn(col).setPreferredWidth(Math.min(maxWidth, 250));
        }
    }
}
