package com.boulangerie.ui.fx;

import javafx.scene.layout.Region;

/**
 * Interface commune à tous les panneaux JavaFX.
 */
public interface FxPanel {
    /** Retourne le nœud racine du panneau. */
    Region getRoot();

    /** Rafraîchit les données depuis la base. */
    void refresh();
}
