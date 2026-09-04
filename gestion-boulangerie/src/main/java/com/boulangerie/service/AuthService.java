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
    private final LoginAttemptService loginAttemptService = LoginAttemptService.getInstance();

    /**
     * Tente la connexion. Retourne l'utilisateur si succès.
     * @throws IllegalArgumentException si identifiants incorrects ou compte inactif.
     */
    public Utilisateur connecter(String login, String motDePasse) {
        String loginNormalise = login == null ? "" : login.trim();
        loginAttemptService.verifierAutorisation(loginNormalise);

        Optional<Utilisateur> opt = utilisateurDAO.findByLogin(loginNormalise);
        if (opt.isEmpty()) {
            auditDAO.log(new JournalAudit(
                "Utilisateur", null, JournalAudit.LOGIN, null, loginNormalise, "Échec: identifiant inconnu"));
            throw new IllegalArgumentException(loginAttemptService.enregistrerEchec(loginNormalise));
        }
        Utilisateur u = opt.get();
        if (!u.isActif()) {
            auditDAO.log(new JournalAudit(
                "Utilisateur", u.getId(), JournalAudit.LOGIN, u.getId(), loginNormalise, "Échec: compte désactivé"));
            loginAttemptService.enregistrerEchec(loginNormalise);
            throw new IllegalArgumentException("Ce compte est désactivé. Contactez l'administrateur.");
        }
        if (!BCrypt.checkpw(motDePasse, u.getMotDePasse())) {
            auditDAO.log(new JournalAudit(
                "Utilisateur", u.getId(), JournalAudit.LOGIN, u.getId(), loginNormalise, "Échec: mot de passe incorrect"));
            throw new IllegalArgumentException(loginAttemptService.enregistrerEchec(loginNormalise));
        }
        // Succès
        loginAttemptService.reinitialiser(loginNormalise);
        session.ouvrir(u);
        utilisateurDAO.updateDerniereConnexion(u.getId());
        auditDAO.log(new JournalAudit(
            "Utilisateur", u.getId(), JournalAudit.LOGIN, u.getId(), loginNormalise, "Connexion réussie"));
        log.info("Connexion réussie: {} ({})", loginNormalise, u.getRole().getNom());
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
