package com.boulangerie.service;

import com.boulangerie.dao.AuditDAO;
import com.boulangerie.dao.UtilisateurDAO;
import com.boulangerie.model.JournalAudit;
import com.boulangerie.model.Utilisateur;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UtilisateurDAO utilisateurDAO = new UtilisateurDAO();
    private final AuditDAO auditDAO = new AuditDAO();
    private final SessionService session = SessionService.getInstance();

    /**
     * Tente la connexion. Retourne l'utilisateur si succès.
     * @throws IllegalArgumentException si identifiants incorrects ou compte inactif.
     */
    public Utilisateur connecter(String login, String motDePasse) {
        Optional<Utilisateur> opt = utilisateurDAO.findByLogin(login.trim());
        if (opt.isEmpty()) {
            auditDAO.log(new JournalAudit("Utilisateur", null, JournalAudit.LOGIN, null, login, "Échec: identifiant inconnu"));
            throw new IllegalArgumentException("Identifiant ou mot de passe incorrect.");
        }
        Utilisateur u = opt.get();
        if (!u.isActif()) {
            throw new IllegalArgumentException("Ce compte est désactivé. Contactez l'administrateur.");
        }
        if (!BCrypt.checkpw(motDePasse, u.getMotDePasse())) {
            auditDAO.log(new JournalAudit("Utilisateur", u.getId(), JournalAudit.LOGIN, u.getId(), login, "Échec: mot de passe incorrect"));
            throw new IllegalArgumentException("Identifiant ou mot de passe incorrect.");
        }
        // Succès
        session.ouvrir(u);
        utilisateurDAO.updateDerniereConnexion(u.getId());
        auditDAO.log(new JournalAudit("Utilisateur", u.getId(), JournalAudit.LOGIN, u.getId(), login, "Connexion réussie"));
        log.info("Connexion réussie: {} ({})", login, u.getRole().getNom());
        return u;
    }

    public void deconnecter() {
        if (session.isConnecte()) {
            auditDAO.log(new JournalAudit("Utilisateur", session.getUserId(), JournalAudit.LOGOUT,
                session.getUserId(), session.getLogin(), "Déconnexion"));
        }
        session.fermer();
    }

    /** Hasher un mot de passe (à utiliser à la création/modification) */
    public static String hasher(String motDePasse) {
        return BCrypt.hashpw(motDePasse, BCrypt.gensalt(12));
    }

    /** Vérifier le mot de passe actuel avant de le changer */
    public boolean verifierMotDePasse(String login, String motDePasse) {
        return utilisateurDAO.findByLogin(login)
            .map(u -> BCrypt.checkpw(motDePasse, u.getMotDePasse()))
            .orElse(false);
    }
}
