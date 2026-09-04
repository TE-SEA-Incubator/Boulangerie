package com.boulangerie.service;

import com.boulangerie.model.Utilisateur;

/**
 * Singleton qui maintient la session utilisateur courante.
 */
public class SessionService {
    private static SessionService instance;
    private Utilisateur utilisateurCourant;
    private long derniereActivite;
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private SessionService() {}

    public static synchronized SessionService getInstance() {
        if (instance == null) instance = new SessionService();
        return instance;
    }

    public void ouvrir(Utilisateur u) {
        this.utilisateurCourant = u;
        this.derniereActivite   = System.currentTimeMillis();
    }

    public void fermer() {
        this.utilisateurCourant = null;
    }

    public Utilisateur getUtilisateur() { return utilisateurCourant; }

    public boolean isConnecte() { return utilisateurCourant != null; }

    /** Retourne true si la session a expiré par inactivité */
    public boolean isExpiree() {
        if (!isConnecte()) return true;
        return (System.currentTimeMillis() - derniereActivite) > TIMEOUT_MS;
    }

    /** Appeler à chaque interaction utilisateur */
    public void rafraichir() { this.derniereActivite = System.currentTimeMillis(); }

    public boolean hasPermission(String code) {
        return isConnecte() && utilisateurCourant.hasPermission(code);
    }

    public boolean isAdmin() {
        return isConnecte() && utilisateurCourant.isAdmin();
    }

    /** Raccourci login */
    public String getLogin() {
        return isConnecte() ? utilisateurCourant.getLogin() : "?";
    }

    /** Raccourci id */
    public String getUserId() {
        return isConnecte() ? utilisateurCourant.getId() : null;
    }
}
