package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CaisseService {
    private static final Logger log = LoggerFactory.getLogger(CaisseService.class);
    private final VersementDAO versementDAO = new VersementDAO();
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
                || v.getDateVersement() == null) {
            throw new IllegalArgumentException("Les montants et la date du versement sont obligatoires.");
        }
        if (v.getMontantAttendu().signum() < 0 || v.getMontantRemis().signum() < 0) {
            throw new IllegalArgumentException("Les montants d'un versement ne peuvent pas être négatifs.");
        }

        BigDecimal ecart = v.getMontantRemis().subtract(v.getMontantAttendu());
        if (ecart.compareTo(BigDecimal.ZERO) != 0
                && (v.getMotifEcart() == null || v.getMotifEcart().isBlank())) {
            throw new IllegalArgumentException("Un motif est obligatoire pour tout écart de caisse.");
        }

        // Déterminer le statut
        if (v.getMontantRemis().compareTo(v.getMontantAttendu()) >= 0) {
            v.setStatut(Versement.Statut.Payé);
        } else if (v.getMontantRemis().compareTo(BigDecimal.ZERO) > 0) {
            v.setStatut(Versement.Statut.Partiel);
        } else {
            v.setStatut(Versement.Statut.EnAttente);
        }

        String versementId = versementDAO.save(v);
        v.setId(versementId);

        // Mettre à jour le solde client
        if (v.getClient() != null) {
            clientDAO.findById(v.getClient().getId()).ifPresent(cl -> {
                BigDecimal nouveauSolde = cl.getSoldeActuel().subtract(v.getMontantRemis());
                clientDAO.updateSolde(cl.getId(), nouveauSolde);
            });
        }

        // Journaliser
        String details = "Versement " + v.getNumero() + " | Attendu=" + v.getMontantAttendu()
            + " Remis=" + v.getMontantRemis() + " Écart=" + ecart;
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
        cl.setMontantEnregistre(versementDAO.getMontantRemisJour(date)); // Compatibilité model
        cl.setMotifEcart(motifEcart);
        cl.calculerTaux();

        if (cl.getEcartTotal().compareTo(BigDecimal.ZERO) != 0
                && (motifEcart == null || motifEcart.isBlank())) {
            throw new IllegalArgumentException("Un motif est obligatoire lorsqu'un écart de clôture existe.");
        }

        cl.setSoldeCloture(cl.getMontantRemis().subtract(cl.getMontantAttendu()));
        cl.setValideParId(session.getUserId());
        versementDAO.saveClotureJournaliere(cl);

        auditDAO.log(new JournalAudit("Cloture", null, JournalAudit.CLOTURE,
            session.getUserId(), session.getLogin(),
            "Clôture " + date + " | Taux=" + cl.getTauxRecouvrement() + "% | Écart=" + cl.getEcartTotal()));
        log.info("Clôture {} validée, taux={}%", date, cl.getTauxRecouvrement());
        return cl;
    }

    /**
     * Charge les données de la feuille de caisse / facturation journalière
     * consolidant les sorties, montants attendus, soldes précédents et versements.
     */
    public List<FicheCaisseLigne> chargerFicheCaisseJournaliere(LocalDate date) {
        FicheJournaliereDAO ficheDAO = new FicheJournaliereDAO();
        List<LigneCommande> sorties = ficheDAO.findLignesByDate(date);
        List<Versement> versements = versementDAO.findByDate(date);
        List<Client> clients = clientDAO.findAll();

        Map<String, FicheCaisseLigne> map = new LinkedHashMap<>();

        // 1. Agréger les sorties par client
        for (LigneCommande ls : sorties) {
            if (ls.getClient() == null) continue;
            String clId = ls.getClient().getId();
            FicheCaisseLigne ligne = map.computeIfAbsent(clId, id -> new FicheCaisseLigne(ls.getClient()));
            ligne.ajouterSortie(ls);
        }

        // 2. Associer les versements effectués
        for (Versement v : versements) {
            if (v.getClient() == null) continue;
            String clId = v.getClient().getId();
            FicheCaisseLigne ligne = map.get(clId);
            if (ligne == null) {
                Client cl = clients.stream().filter(c -> c.getId().equals(clId)).findFirst().orElse(v.getClient());
                ligne = new FicheCaisseLigne(cl);
                map.put(clId, ligne);
            }
            ligne.setVersement(v);
        }

        // 3. Pour les clients ayant un solde débiteur non nul même sans sorties ce jour
        for (Client cl : clients) {
            if (cl.getSoldeActuel() != null && cl.getSoldeActuel().compareTo(BigDecimal.ZERO) > 0) {
                map.computeIfAbsent(cl.getId(), id -> new FicheCaisseLigne(cl));
            }
        }

        return new ArrayList<>(map.values());
    }

    /**
     * Encaisse une ligne de caisse avec mise à jour immédiate du solde client et émission du reçu.
     */
    public Versement encaisserLigneCaisse(FicheCaisseLigne ligne, BigDecimal montantRecu, String modePaiement, String motifEcart) {
        if (!session.hasPermission("CAISSE_WRITE")) throw new SecurityException("Permission refusée : CAISSE_WRITE");
        if (ligne == null || ligne.getClient() == null) throw new IllegalArgumentException("Client requis pour l'encaissement.");
        if (montantRecu == null || montantRecu.signum() < 0) throw new IllegalArgumentException("Le montant reçu doit être positif ou nul.");

        Versement v = new Versement();
        v.setNumero(versementDAO.genererNumero());
        v.setDateVersement(LocalDate.now());
        v.setClient(ligne.getClient());
        v.setCaissier(session.getUtilisateur());
        v.setModePaiement(modePaiement != null && !modePaiement.isBlank() ? modePaiement : "Espèces");
        v.setMontantAttendu(ligne.getTotalSolde());
        v.setMontantRemis(montantRecu);
        v.setMontantEnregistre(montantRecu);

        BigDecimal ecart = montantRecu.subtract(ligne.getTotalSolde());
        if (motifEcart != null && !motifEcart.isBlank()) {
            v.setMotifEcart(motifEcart);
        } else if (ecart.compareTo(BigDecimal.ZERO) < 0) {
            v.setMotifEcart("Paiement partiel — Reste à recouvrer : " + ecart.abs() + " FCFA");
        } else {
            v.setMotifEcart("");
        }

        enregistrerVersement(v);
        ligne.setVersement(v);
        return v;
    }
}
