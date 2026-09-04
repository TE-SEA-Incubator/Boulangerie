package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service de déblocage exceptionnel (réservé au Manager / Admin).
 *
 * Workflow CDC §5.7 :
 *   Blocage → Justification → Autorisation Manager
 *   (motif + engagement + montant + durée) → Sortie exceptionnelle
 *   → Dette conservée → Nouveau suivi.
 */
public class DeblocageService {
    private static final Logger log = LoggerFactory.getLogger(DeblocageService.class);

    private final ClientDAO              clientDAO   = new ClientDAO();
    private final AutorisationDeblocageDAO authDAO   = new AutorisationDeblocageDAO();
    private final AuditDAO               auditDAO    = new AuditDAO();
    private final SessionService         session     = SessionService.getInstance();

    /**
     * Déblocage exceptionnel d'un client par le Manager.
     * Crée une AutorisationDeblocage, lève le blocage, journalise.
     *
     * @param clientId         UUID du client
     * @param motif            raison du déblocage
     * @param engagementClient engagement pris par le client
     * @param montantAutorise  montant maximal autorisé pour la sortie
     * @param dureeValidite    date limite de validité de l'autorisation
     */
    public AutorisationDeblocage debloquerExceptionnel(
            String clientId,
            String motif,
            String engagementClient,
            BigDecimal montantAutorise,
            LocalDate dureeValidite) {

        if (!session.hasPermission("DEBLOCAGE_WRITE")) {
            throw new SecurityException("Seul le Manager/Admin peut effectuer un déblocage exceptionnel.");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif du déblocage est obligatoire.");
        }

        // Créer l'autorisation
        AutorisationDeblocage auth = new AutorisationDeblocage();
        auth.setClientId(clientId);
        auth.setManagerId(session.getUserId());
        auth.setMotif(motif);
        auth.setEngagementClient(engagementClient);
        auth.setMontantAutorise(montantAutorise);
        auth.setDureeValidite(dureeValidite);
        authDAO.save(auth);

        // Lever le blocage
        clientDAO.leverBlocage(clientId, session.getUserId());
        clientDAO.updateStatut(clientId, "Actif");

        // Journaliser
        auditDAO.log(new JournalAudit(
            "Client", clientId, JournalAudit.UNBLOCK,
            session.getUserId(), session.getLogin(),
            "Déblocage exceptionnel — Motif: " + motif
            + " | Montant autorisé: " + (montantAutorise != null ? montantAutorise : "N/A")
            + " | Validité: " + (dureeValidite != null ? dureeValidite : "illimité")
        ));
        log.info("Client {} débloqué exceptionnellement par {}", clientId, session.getLogin());
        return auth;
    }

    /**
     * Blocage manuel d'un client.
     */
    public void bloquerClient(String clientId, String motif, BigDecimal montantDette) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif est obligatoire pour bloquer un client.");
        }
        Blocage b = new Blocage();
        b.setClientId(clientId);
        b.setDateBlocage(LocalDate.now());
        b.setMotif(motif);
        b.setMontantDette(montantDette);
        clientDAO.saveBlocage(b);
        clientDAO.updateStatut(clientId, "Bloqué");

        auditDAO.log(new JournalAudit(
            "Client", clientId, JournalAudit.BLOCK,
            session.getUserId(), session.getLogin(),
            "Blocage manuel — Motif: " + motif + " | Dette: " + montantDette
        ));
        log.info("Client {} bloqué par {}", clientId, session.getLogin());
    }
}
