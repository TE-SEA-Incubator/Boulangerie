package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CaisseService {
    private static final Logger log = LoggerFactory.getLogger(CaisseService.class);
    private final VersementDAO versementDAO = new VersementDAO();
    private final FactureDAO   factureDAO   = new FactureDAO();
    private final ClientDAO    clientDAO    = new ClientDAO();
    private final AuditDAO     auditDAO     = new AuditDAO();
    private final SessionService session    = SessionService.getInstance();

    /**
     * Enregistre un versement et met à jour le solde client.
     * Un écart non nul nécessite obligatoirement un motif.
     */
    public Versement enregistrerVersement(Versement v) {
        if (!session.hasPermission("CAISSE_WRITE")) throw new SecurityException("Permission refusée : CAISSE_WRITE");
        if (v == null || v.getMontantAttendu() == null || v.getMontantRemis() == null
                || v.getMontantEnregistre() == null || v.getDateVersement() == null) {
            throw new IllegalArgumentException("Les montants et la date du versement sont obligatoires.");
        }
        if (v.getMontantAttendu().signum() < 0 || v.getMontantRemis().signum() < 0
                || v.getMontantEnregistre().signum() < 0) {
            throw new IllegalArgumentException("Les montants d'un versement ne peuvent pas être négatifs.");
        }

        BigDecimal ecart = v.getMontantRemis().subtract(v.getMontantEnregistre());
        if (ecart.compareTo(BigDecimal.ZERO) != 0
                && (v.getMotifEcart() == null || v.getMotifEcart().isBlank())) {
            throw new IllegalArgumentException("Un motif est obligatoire pour tout écart de caisse.");
        }

        // Déterminer le statut
        if (v.getMontantEnregistre().compareTo(v.getMontantAttendu()) >= 0) {
            v.setStatut(Versement.Statut.Payé);
        } else if (v.getMontantEnregistre().compareTo(BigDecimal.ZERO) > 0) {
            v.setStatut(Versement.Statut.Partiel);
        } else {
            v.setStatut(Versement.Statut.EnAttente);
        }

        String versementId = versementDAO.save(v);
        v.setId(versementId);

        // Mettre à jour le solde client
        if (v.getClient() != null) {
            clientDAO.findById(v.getClient().getId()).ifPresent(cl -> {
                BigDecimal nouveauSolde = cl.getSoldeActuel().subtract(v.getMontantEnregistre());
                clientDAO.updateSolde(cl.getId(), nouveauSolde);
            });
        }

        // Mettre à jour le statut de la facture
        if (v.getFacture() != null) {
            factureDAO.findById(v.getFacture().getId()).ifPresent(f -> {
                BigDecimal totalVerse = versementDAO.findByFacture(f.getId()).stream()
                    .map(Versement::getMontantEnregistre).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalVerse.compareTo(f.getMontantTtc()) >= 0) {
                    factureDAO.updateStatut(f.getId(), Facture.Statut.Payée);
                } else if (totalVerse.compareTo(BigDecimal.ZERO) > 0) {
                    factureDAO.updateStatut(f.getId(), Facture.Statut.Partielle);
                }
            });
        }

        // Journaliser
        String details = "Versement " + v.getNumero() + " | Attendu=" + v.getMontantAttendu()
            + " Remis=" + v.getMontantRemis() + " Enregistré=" + v.getMontantEnregistre()
            + " Écart=" + ecart;
        if (ecart.compareTo(BigDecimal.ZERO) != 0) {
            auditDAO.log(new JournalAudit("Versement", versementId, JournalAudit.ECART,
                session.getUserId(), session.getLogin(), details + " Motif: " + v.getMotifEcart()));
        }
        auditDAO.log(new JournalAudit("Versement", versementId, JournalAudit.CREATE,
            session.getUserId(), session.getLogin(), details));

        // Générer le reçu automatiquement
        genererRecu(v);
        log.info("Versement {} enregistré", v.getNumero());
        return v;
    }

    /** Génère un reçu électronique pour un versement */
    public Recu genererRecu(Versement v) {
        Recu r = new Recu();
        r.setNumero(versementDAO.genererNumeroRecu());
        r.setVersement(v);
        r.setGenerePar(session.getUserId());
        versementDAO.saveRecu(r);
        return r;
    }

    /**
     * Valide et clôture la caisse du jour.
     */
    public ClotureJournaliere cloturerJour(LocalDate date, String motifEcart) {
        if (!session.hasPermission("CLOTURE_WRITE")) throw new SecurityException("Permission refusée : CLOTURE_WRITE");
        if (date == null) throw new IllegalArgumentException("La date de clôture est obligatoire.");

        ClotureJournaliere cl = new ClotureJournaliere();
        cl.setDateCloture(date);
        cl.setMontantAttendu(versementDAO.getMontantAttenduJour(date));
        cl.setMontantRemis(versementDAO.getMontantRemisJour(date));
        cl.setMontantEnregistre(versementDAO.getMontantEnregistreJour(date));
        cl.setMotifEcart(motifEcart);
        cl.calculerTaux();

        if (cl.getEcartTotal().compareTo(BigDecimal.ZERO) != 0
                && (motifEcart == null || motifEcart.isBlank())) {
            throw new IllegalArgumentException("Un motif est obligatoire lorsqu'un écart de clôture existe.");
        }

        BigDecimal totalSoldesClients = BigDecimal.ZERO; // simplifié; peut être calculé depuis solde_client
        cl.setSoldeCloture(cl.getMontantEnregistre().subtract(cl.getMontantAttendu()));
        cl.setValideParId(session.getUserId());
        versementDAO.saveClotureJournaliere(cl);

        auditDAO.log(new JournalAudit("Cloture", null, JournalAudit.CLOTURE,
            session.getUserId(), session.getLogin(),
            "Clôture " + date + " | Taux=" + cl.getTauxRecouvrement() + "% | Écart=" + cl.getEcartTotal()));
        log.info("Clôture {} validée, taux={}%", date, cl.getTauxRecouvrement());
        return cl;
    }
}
