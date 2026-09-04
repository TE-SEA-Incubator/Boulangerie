package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Génère automatiquement une facture à partir d'une fiche journalière.
 * La facture est verrouillée dès sa création : toute correction passe par un avoir.
 */
public class FacturationService {
    private static final Logger log = LoggerFactory.getLogger(FacturationService.class);
    private final FactureDAO factureDAO = new FactureDAO();
    private final ClientDAO  clientDAO  = new ClientDAO();
    private final AuditDAO   auditDAO   = new AuditDAO();
    private final SessionService session = SessionService.getInstance();

    /** TVA appliquée (0 si non soumis à TVA locale) */
    private static final BigDecimal TVA_PCT = BigDecimal.ZERO;

    /**
     * Génère une facture par client à partir d'une fiche journalière.
     * @return liste des factures créées
     */
    public List<Facture> genererDepuisFiche(FicheJournaliere fiche) {
        if (fiche == null || fiche.getId() == null) {
            throw new IllegalArgumentException("La fiche à facturer est invalide.");
        }
        if (factureDAO.existsForFiche(fiche.getId())) {
            throw new IllegalStateException("Cette fiche a déjà été facturée.");
        }
        if (fiche.getLignes() == null || fiche.getLignes().isEmpty()) {
            throw new IllegalStateException("Impossible de facturer une fiche sans ligne de sortie.");
        }
        // Regrouper les lignes par client
        Map<String, List<LigneSortie>> parClient = new LinkedHashMap<>();
        for (LigneSortie l : fiche.getLignes()) {
            parClient.computeIfAbsent(l.getClient().getId(), k -> new ArrayList<>()).add(l);
        }

        List<Facture> factures = new ArrayList<>();
        for (Map.Entry<String, List<LigneSortie>> entry : parClient.entrySet()) {
            String clientId = entry.getKey();
            List<LigneSortie> lignes = entry.getValue();
            Optional<Client> opt = clientDAO.findById(clientId);
            if (opt.isEmpty()) continue;
            Client client = opt.get();

            BigDecimal totalHt = lignes.stream()
                .map(LigneSortie::getMontantHt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalHt.compareTo(BigDecimal.ZERO) == 0) continue;

            Facture f = new Facture();
            f.setNumero(factureDAO.genererNumero(fiche.getDateFiche()));
            f.setDateEmission(fiche.getDateFiche());
            f.setClient(client);
            f.setLivreur(fiche.getLivreur());
            f.setMontantHt(totalHt);
            f.setTvaPct(TVA_PCT);
            f.calculerTva();
            f.setFicheId(fiche.getId());
            f.setEstVerrouillee(true);
            f.setCreePar(session.getUserId());

            // Vérifier dépassement délai → blocage automatique
            if (client.isNominatif()) {
                f.setStatut(Facture.Statut.EnAttente);
                // Mise à jour du solde client
                BigDecimal nouveauSolde = client.getSoldeActuel().add(f.getMontantTtc());
                clientDAO.updateSolde(clientId, nouveauSolde);
                // Vérifier blocage automatique
                verifierBlocageAutomatique(client, nouveauSolde);
            }

            String factureId = factureDAO.save(f);
            f.setId(factureId);
            factures.add(f);

            auditDAO.log(new JournalAudit("Facture", factureId, JournalAudit.CREATE,
                session.getUserId(), session.getLogin(),
                "Facture " + f.getNumero() + " créée pour " + client.getNom() + " - " + f.getMontantTtc() + " FCFA"));
            log.info("Facture {} créée pour {}", f.getNumero(), client.getNom());
        }
        return factures;
    }

    /**
     * Crée un avoir sur une facture verrouillée.
     */
    public Avoir creerAvoir(Facture facture, BigDecimal montant, String motif) {
        if (!facture.isEstVerrouillee()) throw new IllegalStateException("La facture n'est pas verrouillée.");
        if (!session.hasPermission("AVOIR_WRITE")) throw new SecurityException("Permission refusée : AVOIR_WRITE");

        Avoir av = new Avoir();
        av.setNumero(factureDAO.genererNumeroAvoir(LocalDate.now()));
        av.setFactureId(facture.getId());
        av.setDateAvoir(LocalDate.now());
        av.setMontant(montant);
        av.setMotif(motif);
        av.setCreePar(session.getUserId());
        factureDAO.saveAvoir(av);

        // Ajuster le solde client
        if (facture.getClient() != null && facture.getClient().isNominatif()) {
            clientDAO.findById(facture.getClient().getId()).ifPresent(cl -> {
                BigDecimal nouveauSolde = cl.getSoldeActuel().subtract(montant);
                clientDAO.updateSolde(cl.getId(), nouveauSolde);
            });
        }

        auditDAO.log(new JournalAudit("Avoir", av.getNumero(), JournalAudit.AVOIR,
            session.getUserId(), session.getLogin(),
            "Avoir " + av.getNumero() + " sur facture " + facture.getNumero() + " - " + montant));
        return av;
    }

    // ── Blocage automatique ──────────────────────────────────────
    private void verifierBlocageAutomatique(Client client, BigDecimal nouveauSolde) {
        boolean depassePlafond = client.getPlafondCredit().compareTo(BigDecimal.ZERO) > 0
            && nouveauSolde.compareTo(client.getPlafondCredit()) > 0;
        boolean delaiDepasse = client.getDerniereFactureDate() != null
            && client.getDelaiPaiement() > 0
            && LocalDate.now().isAfter(client.getDerniereFactureDate().plusDays(client.getDelaiPaiement()));
        if ((depassePlafond || delaiDepasse) && !client.isBloque()) {
            clientDAO.updateStatut(client.getId(), "Bloqué");
            Blocage b = new Blocage();
            b.setClientId(client.getId());
            b.setDateBlocage(LocalDate.now());
            b.setMotif(depassePlafond
                ? "Dépassement du plafond de crédit (" + nouveauSolde + " > " + client.getPlafondCredit() + ")"
                : "Dépassement du délai de paiement (" + client.getDelaiPaiement() + " jours)");
            b.setMontantDette(nouveauSolde);
            clientDAO.saveBlocage(b);
            auditDAO.log(new JournalAudit("Client", client.getId(), JournalAudit.BLOCK,
                session.getUserId(), session.getLogin(),
                "Blocage automatique: dépassement plafond - solde " + nouveauSolde));
            log.warn("Client {} bloqué automatiquement: solde={}", client.getNom(), nouveauSolde);
        }
    }
}
